import type { Role } from '../api/types';

/**
 * 로그인 전에 막혔던 화면(RequireRole의 state.from)이 이 역할로 들어갈 수 있는 곳인지.
 * 고객이 보던 /orders를 관리자로 로그인한 뒤 열면 "고객 계정만…" 안내만 보이므로, 맞지 않으면 역할 기본 화면으로 보냄.
 * 규칙은 App.tsx의 RequireRole roles와 같게 유지 (화면 이동 편의용 — 최종 권한 판단은 서버 403)
 */
const ROLE_PREFIXES: { prefix: string; roles: Role[] }[] = [
  { prefix: '/cart', roles: ['CUSTOMER'] },
  { prefix: '/payments', roles: ['CUSTOMER'] },
  { prefix: '/orders', roles: ['CUSTOMER', 'OWNER'] },
  { prefix: '/owner', roles: ['OWNER'] },
  { prefix: '/admin', roles: ['ADMIN'] },
  { prefix: '/notifications', roles: ['CUSTOMER', 'OWNER', 'ADMIN'] },
  { prefix: '/me', roles: ['CUSTOMER', 'OWNER', 'ADMIN'] },
];

function matches(path: string, prefix: string): boolean {
  return path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`);
}

export function canRoleVisit(path: string, role: Role): boolean {
  // 같은 사이트 안의 경로만 (//evil.com 같은 외부 주소로 보내지 않음)
  if (!path.startsWith('/') || path.startsWith('//')) return false;
  const rule = ROLE_PREFIXES.find((r) => matches(path, r.prefix));
  // 목록에 없는 경로(로그인·가입 화면 등)로는 돌려보내지 않음 — from은 RequireRole이 막은 화면에서만 옴
  return rule != null && rule.roles.includes(role);
}

/** 로그인 직후 이동할 곳 — 이전 화면이 이 역할에 맞으면 그곳, 아니면 역할 기본 화면 */
export function postLoginPath(from: string | undefined, role: Role, roleHome: Record<Role, string>): string {
  return from && canRoleVisit(from, role) ? from : roleHome[role];
}
