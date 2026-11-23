import { authApi } from '../api/endpoints';
import type { Role } from '../api/types';

/** 서버가 내려 주는 체험 계정 (GET /api/users/demo-accounts) — 관리자는 password가 null */
export interface DemoAccount {
  role: Role;
  username: string;
  password: string | null;
}

export const DEMO_LABEL: Record<Role, { title: string; hint: string }> = {
  CUSTOMER: { title: '고객 체험하기', hint: '메뉴 담기 · 주문 · 결제' },
  OWNER: { title: '사장님 체험하기', hint: '주문 수락 · 매출 · 정산' },
  ADMIN: { title: '관리자 체험하기', hint: '정산 확정·지급 · 사장님 승인' },
};

const ORDER: Role[] = ['CUSTOMER', 'OWNER', 'ADMIN'];

/**
 * 로그인 화면 "체험하기" 버튼 순서 — 서버 순서와 상관없이 고객 → 사장님 → 관리자
 * 모르는 역할, 비밀번호 없는 고객·사장님(채울 값이 없음)은 버림. 관리자는 비밀번호 없이 체험 로그인 API를 씀
 */
export function orderDemoAccounts(list: DemoAccount[]): DemoAccount[] {
  return list
    .filter((a) => ORDER.includes(a.role) && (a.role === 'ADMIN' || !!a.password))
    .sort((a, b) => ORDER.indexOf(a.role) - ORDER.indexOf(b.role));
}

/**
 * 체험 계정 목록 — 서버에서 받음 (데모 데이터가 꺼진 배포는 빈 배열 → 버튼·DEMO 표시 없음)
 * 예전에는 빌드 환경 변수(VITE_DEMO_ACCOUNTS)로 받았으나, 관리자 비밀번호가 빌드 결과에 보이지 않도록 서버로 옮김
 * 로그인 화면과 상단 DEMO 표시가 함께 쓰므로 한 번만 요청. 실패하면 다음 화면 진입 때 다시 시도
 */
let cached: Promise<DemoAccount[]> | null = null;
export function loadDemoAccounts(): Promise<DemoAccount[]> {
  if (!cached) {
    cached = authApi
      .demoAccounts()
      .then(orderDemoAccounts)
      .catch(() => {
        cached = null;
        return [];
      });
  }
  return cached;
}
