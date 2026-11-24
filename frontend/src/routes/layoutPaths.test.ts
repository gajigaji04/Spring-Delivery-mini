import { describe, expect, it } from 'vitest';
import { isBarePath } from './layoutPaths';

describe('isBarePath', () => {
  it('AuthShell 화면은 앱 헤더 없이 — 헤더 중복 방지', () => {
    for (const p of ['/login', '/signup', '/password-reset', '/email-verification', '/oauth/callback']) {
      expect(isBarePath(p)).toBe(true);
    }
  });

  it('랜딩도 자체 헤더', () => {
    expect(isBarePath('/')).toBe(true);
  });

  it('끝 슬래시가 붙어도 같은 화면으로 본다', () => {
    expect(isBarePath('/password-reset/')).toBe(true);
  });

  it('앱 화면은 헤더 사용', () => {
    for (const p of ['/stores', '/me', '/orders/1', '/admin/settlements', '/password-reset/x']) {
      expect(isBarePath(p)).toBe(false);
    }
  });
});
