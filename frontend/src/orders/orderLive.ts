import type { OrderStatus } from '../api/types';

/**
 * 고객 화면 주문 상태 자동 갱신 (사장님 수락·배달완료를 새로고침 없이 반영)
 *
 * 왜 SSE가 아니라 폴링인가: 기존 SSE(/api/owner/orders/stream)는 사장님 매장 단위 구독이고 인스턴스 메모리에 연결을 둠
 *   (docs/architecture.md 실시간 주문 알림 장). 고객용 구독을 따로 열면 무료 배포(0.1 CPU) 연결 수가 늘고 범위가 커짐
 *   → 고객이 보는 화면에서, 사장님이 바꿀 수 있는 상태일 때만 짧게 다시 조회. 화면이 숨겨지면 멈추고 돌아오면 바로 조회
 */
export const ORDER_POLL_MS = 10_000;

/** 사장님 처리로 바뀔 수 있는 상태 — 결제완료(→수락), 수락(→배달완료). 결제 전·취소·배달완료는 고객 본인 행동이나 끝난 상태 */
export function isAwaitingStoreUpdate(status: OrderStatus): boolean {
  return status === 'PAYMENT_COMPLETED' || status === 'ORDER_ACCEPTED';
}

/** 고객이 취소할 수 있는 상태 (서버 Order.cancel과 같은 규칙 — 서버가 최종 판단, 409면 화면을 다시 불러옴) */
export function isCancellable(status: OrderStatus): boolean {
  return status === 'ORDER_REQUEST' || status === 'PAYMENT_COMPLETED';
}
