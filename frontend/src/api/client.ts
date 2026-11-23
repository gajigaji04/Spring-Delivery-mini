import { tokenStore } from '../auth/tokenStore';
import { createServerWake, isGatewayFailure } from './serverWake';
import type { ApiErrorBody, TokenResponse } from './types';

// 빈 값('')이면 같은 출처(/api/...)로 호출 — Vercel rewrite가 API를 프록시하는 배포에서 Refresh 쿠키를
// 다른 사이트(third-party) 쿠키가 아닌 같은 사이트 쿠키로 받기 위해 (docs/operations.md 8장)
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8090';
// 사장님 실시간 주문 알림(SSE)만 다른 주소로 보낼 때 (선택) — 프록시가 스트림을 끊거나 모아 보내면 API 주소로 직접 연결.
// SSE는 쿠키 없이 Bearer만 쓰므로 다른 출처여도 CORS 허용만 있으면 됨
export const STREAM_BASE_URL: string = import.meta.env.VITE_STREAM_BASE_URL || BASE_URL;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly traceId: string | null;

  constructor(status: number, message: string, code: string | null, traceId: string | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
  headers?: Record<string, string>;
  /** false면 Authorization 헤더를 붙이지 않고 401 재발급도 하지 않음 (로그인·가입 등) */
  auth?: boolean;
  /** login·refresh·logout만 true — Refresh 쿠키는 Path=/api/users라 다른 API에는 필요 없음 */
  withCredentials?: boolean;
}

export interface ApiResponse<T> {
  data: T;
  headers: Headers;
}

/** 목록 응답: 본문은 배열, 페이지 정보는 헤더 (docs/api.md 공통 사항 — 페이지네이션·keyset) */
export interface PageResult<T> {
  items: T[];
  hasNext: boolean;
  nextAfterId: number | null;
}

export function buildUrl(path: string, query?: RequestOptions['query'], base: string = BASE_URL): string {
  // 상대 주소(같은 출처)도 처리하도록 현재 페이지 주소를 기준으로 둠 (테스트 등 location이 없으면 절대 주소만)
  const url = new URL(base + path, globalThis.location?.origin);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      // 선택 조건은 값이 있을 때만 보냄 — 빈 문자열을 보내면 서버에서 형식 오류(400)
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

export async function toApiError(res: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {};
  try {
    body = await res.json();
  } catch {
    // CORS 거부 403 등은 공통 오류 형식이 아님 → 상태 코드만으로 처리
  }
  return new ApiError(
    res.status,
    body.message ?? `요청에 실패했습니다 (${res.status})`,
    body.code ?? null,
    body.traceId ?? res.headers.get('X-Trace-Id'),
  );
}

// ---- 서버 깨우기 (무료 호스팅의 절전 대응, serverWake.ts) ----
// 확인용 요청: 인증 없이 받는 가벼운 공개 GET. 서버(Spring)가 응답하면 4xx라도 깨어 있는 것
// 503도 준비 안 됨으로 봄 — 이 API는 서버가 503을 내지 않으므로 503은 플랫폼·프록시 쪽 응답
export const serverWake = createServerWake({
  probe: async () => {
    const res = await fetch(buildUrl('/api/users/social/providers'));
    return !isGatewayFailure(res.status) && res.status !== 503;
  },
});

// ---- Access Token 재발급 (single-flight) ----
// 같은 Refresh Token으로 두 번 재발급하면 서버가 재사용(REFRESH_TOKEN_REUSED)으로 보고 로그인 전체를 폐기함
// → 탭 안에서는 진행 중인 Promise를 공유하고, 탭 사이에서는 Web Locks로 순서를 맞춘다
let refreshing: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  // 서버가 깨는 중이면 기다린 뒤 보냄 — 게이트웨이 시간 초과로 실패하면 로그인이 풀리고, 늦게 도착한 같은 요청과
  // 다시 보낸 요청이 겹치면 Refresh 재사용으로 보여 로그인 전체가 폐기될 수 있으므로 재발급은 재시도하지 않음
  await serverWake.whenSettled();
  const res = await fetch(buildUrl('/api/users/refresh'), { method: 'POST', credentials: 'include' });
  if (!res.ok) {
    tokenStore.clear();
    return false;
  }
  const body = (await res.json()) as TokenResponse;
  tokenStore.set(body.accessToken);
  return true;
}

export function refreshAccessToken(): Promise<boolean> {
  if (!refreshing) {
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    // lib.dom 타입은 콜백의 Promise를 한 겹 더 감싸 선언함 → then으로 풀어 Promise<boolean>으로 맞춤
    const run = locks ? locks.request('delivery-mini:refresh', doRefresh).then((ok) => ok) : doRefresh();
    refreshing = run.finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export async function request<T>(
  path: string,
  options: RequestOptions = {},
  retried = false,
  wakeRetried = false,
): Promise<ApiResponse<T>> {
  const { method = 'GET', body, query, headers = {}, auth = true, withCredentials = false } = options;
  const finalHeaders: Record<string, string> = { ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (auth && token) finalHeaders['Authorization'] = `Bearer ${token}`;

  // 서버가 깨는 중이면 응답할 때까지 기다렸다가 보냄 (자는 서버에 보내 게이트웨이 오류로 끝나지 않도록)
  await serverWake.whenSettled();

  // 서버가 자고 있다는 신호(연결 실패·502·504)를 받으면 깨우기 시작(안내 배너)
  // GET은 서버가 깨어난 뒤 한 번 다시 보냄. 그 밖의 요청(결제 등)은 서버에 도착했는지 알 수 없어 자동으로 다시 보내지 않음
  const retryAfterWake = async (failure: unknown): Promise<ApiResponse<T>> => {
    const awake = await serverWake.ensureAwake();
    if (awake && method === 'GET' && !wakeRetried) return request<T>(path, options, retried, true);
    throw failure;
  };

  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers: finalHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: withCredentials ? 'include' : 'same-origin',
    });
  } catch (e) {
    return retryAfterWake(e);
  }
  if (isGatewayFailure(res.status)) {
    return retryAfterWake(await toApiError(res));
  }

  if (!res.ok) {
    const error = await toApiError(res);
    if (res.status === 401 && auth) {
      // 분기는 message 문구가 아니라 code로 — TOKEN_EXPIRED만 재발급 후 1번 재시도
      if (error.code === 'TOKEN_EXPIRED' && !retried && (await refreshAccessToken())) {
        return request<T>(path, options, true, wakeRetried);
      }
      // TOKEN_INVALID·재발급 실패 등은 로그인 화면으로 (AuthProvider가 토큰 삭제를 구독)
      tokenStore.clear();
    }
    throw error;
  }

  const data = res.status === 204 ? (undefined as T) : ((await res.json()) as T);
  return { data, headers: res.headers };
}

export async function get<T>(path: string, query?: RequestOptions['query'], auth = true): Promise<T> {
  return (await request<T>(path, { query, auth })).data;
}

export async function getPage<T>(path: string, query?: RequestOptions['query'], auth = true): Promise<PageResult<T>> {
  const { data, headers } = await request<T[]>(path, { query, auth });
  const nextAfterId = headers.get('X-Next-After-Id');
  return {
    items: data,
    hasNext: headers.get('X-Has-Next') === 'true',
    nextAfterId: nextAfterId ? Number(nextAfterId) : null,
  };
}
