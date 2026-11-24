import { describe, expect, it } from 'vitest';
import type { OrderStatus } from '../api/types';
import { isAwaitingStoreUpdate, isCancellable } from './orderLive';

const ALL: OrderStatus[] = ['ORDER_REQUEST', 'ORDER_CANCELLED', 'PAYMENT_COMPLETED', 'ORDER_ACCEPTED', 'DELIVERY_COMPLETED'];

describe('orderLive', () => {
  it('사장님이 바꿀 수 있는 결제완료·수락 상태에서만 자동 갱신한다', () => {
    expect(ALL.filter(isAwaitingStoreUpdate)).toEqual(['PAYMENT_COMPLETED', 'ORDER_ACCEPTED']);
  });

  it('취소 버튼은 수락 전(주문요청·결제완료)에만 — 배달 중(수락)·배달완료·취소된 주문에는 없다', () => {
    expect(ALL.filter(isCancellable)).toEqual(['ORDER_REQUEST', 'PAYMENT_COMPLETED']);
  });
});
