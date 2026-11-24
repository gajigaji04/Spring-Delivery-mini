/**
 * 메일 링크의 토큰 읽기 — 비밀번호 재설정(V23)·이메일 인증(V24) 공통.
 * 메일 링크는 `/password-reset#token=...`·`/email-verification#token=...` — 쿼리가 아니라 조각이라 서버·프록시 로그와 Referer에 남지 않는다.
 * 형식(43자 Base64 URL-safe)이 아니면 null.
 */
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{43}$/;

export function readResetToken(hash: string): string | null {
  return readFragmentToken(hash).token;
}

/**
 * 조각 토큰 + "링크가 깨졌는지" 구분.
 * - token: 형식이 맞는 토큰 (없으면 null)
 * - invalid: `token=`이 있었는데 형식이 틀림 — 메일 앱이 링크를 자르거나 바꾼 경우. 아예 없는 경우(직접 들어옴)와 안내를 다르게 하려고
 */
export function readFragmentToken(hash: string): { token: string | null; invalid: boolean } {
  const params = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
  if (!params.has('token')) return { token: null, invalid: false };
  const token = params.get('token') ?? '';
  return TOKEN_FORMAT.test(token) ? { token, invalid: false } : { token: null, invalid: true };
}
