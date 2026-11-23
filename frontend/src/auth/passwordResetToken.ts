/**
 * 비밀번호 재설정 링크의 토큰 읽기 (V23).
 * 메일 링크는 `/password-reset#token=...` — 쿼리가 아니라 조각이라 서버·프록시 로그와 Referer에 남지 않는다.
 * 형식(43자 Base64 URL-safe)이 아니면 null → 화면은 이메일 입력(링크 요청) 단계로 시작.
 */
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{43}$/;

export function readResetToken(hash: string): string | null {
  const params = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
  const token = params.get('token');
  return token && TOKEN_FORMAT.test(token) ? token : null;
}
