import type { DemoAccount } from '../auth/demoAccounts';

/** 서버 OwnerApplicationRejectRequest와 같은 한도 (255자, 앞뒤 공백 제외) */
export const REJECT_REASON_MAX = 255;

/**
 * 거절 사유 검사 — 통과하면 null, 아니면 화면에 보일 안내 문장
 * 서버도 빈 사유를 400으로 막지만, 요청 전에 막아 "눌렀는데 아무 일도 없음"이 되지 않도록 이유를 보여 줌
 */
export function rejectReasonError(reason: string): string | null {
  const trimmed = reason.trim();
  if (!trimmed) return '거절 사유를 입력해 주세요. 신청자에게 그대로 보입니다.';
  if (trimmed.length > REJECT_REASON_MAX) return `거절 사유는 ${REJECT_REASON_MAX}자 이하로 입력해 주세요.`;
  return null;
}

/**
 * 지금 로그인한 관리자가 "관리자 체험하기" 계정인지 — 체험 계정 목록(데모 배포에서만 내려옴)의 관리자 아이디와 비교
 * 데모 관리자는 서버(DemoAdminScope)가 데모 회원의 신청만 돌려주므로, 다른 회원 신청이 안 보이는 이유를 안내하는 데 씀
 */
export function isDemoAdmin(username: string | undefined, demoAccounts: DemoAccount[] | null | undefined): boolean {
  if (!username || !demoAccounts) return false;
  return demoAccounts.some((a) => a.role === 'ADMIN' && a.username === username);
}
