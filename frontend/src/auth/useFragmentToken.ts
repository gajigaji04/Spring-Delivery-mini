import { useEffect, useState } from 'react';
import { readFragmentToken } from './passwordResetToken';

/**
 * 메일 링크 조각(#token=)을 화면 상태로 옮기고 주소창에서 지움 — 뒤로 가기·방문 기록·화면 공유에 토큰이 남지 않도록.
 *
 * 읽기(useState 초기값)와 지우기(useEffect)를 나눈 이유:
 * 초기값 함수 안에서 replaceState로 조각을 지우면, React가 첫 렌더를 버리고 다시 그릴 때
 * (lazy 화면 로드 직후 로그인 복구·서버 깨우기 같은 다른 갱신이 끼어들면 동시 렌더가 처음부터 다시 시작됨)
 * 두 번째 초기값 계산은 이미 비어 있는 주소를 읽어 토큰이 사라진다 → 배포에서 재설정 링크가 "이메일 입력" 화면으로 열리던 원인.
 * 렌더는 읽기만 하고, 화면이 실제로 붙은(commit) 뒤에만 지우면 렌더를 몇 번 다시 해도 같은 토큰을 읽는다.
 */
export function useFragmentToken(): { token: string | null; invalid: boolean } {
  const [state] = useState(() => readFragmentToken(window.location.hash));

  useEffect(() => {
    if (!window.location.hash) return;
    // history.state는 그대로 둠 — React Router가 넣어 둔 key·idx를 null로 덮으면 뒤로 가기 기록이 어긋남
    const { pathname, search } = window.location;
    window.history.replaceState(window.history.state, '', pathname + search);
  }, []);

  return state;
}
