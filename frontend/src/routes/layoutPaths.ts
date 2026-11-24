/**
 * 앱 헤더(Layout)를 쓰지 않는 경로 — 자체 화면 틀이 있는 곳.
 * - 랜딩: 공식 홈페이지 헤더
 * - AuthShell(로고·상단 링크 헤더 + 폼 패널) 화면: 로그인·가입·비밀번호 찾기·이메일 인증·소셜 로그인 결과
 *   여기에 앱 헤더까지 붙으면 로고·로그인 링크가 두 줄로 겹쳐 보임
 */
const BARE_PATHS = new Set(['/', '/login', '/signup', '/password-reset', '/email-verification', '/oauth/callback']);

export function isBarePath(pathname: string): boolean {
  // 끝 슬래시(/password-reset/)로 들어와도 같은 화면
  const p = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  return BARE_PATHS.has(p);
}
