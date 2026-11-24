import { describe, expect, it } from 'vitest';
import { canRoleVisit, postLoginPath } from './postLoginPath';

const HOME = { CUSTOMER: '/stores', OWNER: '/owner', ADMIN: '/admin/settlements' } as const;

describe('postLoginPath', () => {
  it('관리자는 고객이 보던 주문·장바구니 화면 대신 관리자 기본 화면으로 간다', () => {
    expect(postLoginPath('/orders/3', 'ADMIN', HOME)).toBe('/admin/settlements');
    expect(postLoginPath('/cart', 'ADMIN', HOME)).toBe('/admin/settlements');
  });

  it('역할에 맞는 이전 화면이면 그대로 돌아간다', () => {
    expect(postLoginPath('/orders/3', 'CUSTOMER', HOME)).toBe('/orders/3');
    expect(postLoginPath('/admin/outbox', 'ADMIN', HOME)).toBe('/admin/outbox');
    expect(postLoginPath('/me', 'OWNER', HOME)).toBe('/me');
  });

  it('이전 화면이 없거나 다른 역할 화면이면 역할 기본 화면', () => {
    expect(postLoginPath(undefined, 'OWNER', HOME)).toBe('/owner');
    expect(postLoginPath('/admin/settlements', 'CUSTOMER', HOME)).toBe('/stores');
    expect(postLoginPath('/payments', 'OWNER', HOME)).toBe('/owner');
  });

  it('외부 주소·접두어만 같은 경로·목록에 없는 경로로는 보내지 않는다', () => {
    expect(canRoleVisit('//evil.example', 'CUSTOMER')).toBe(false);
    expect(canRoleVisit('https://evil.example', 'CUSTOMER')).toBe(false);
    expect(canRoleVisit('/ownerx', 'OWNER')).toBe(false);
    expect(canRoleVisit('/login', 'CUSTOMER')).toBe(false);
  });
});
