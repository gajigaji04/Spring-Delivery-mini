import type { Menu } from '../api/types';

export interface StoredCartLine {
  menu: Menu;
  quantity: number;
}

/** 저장소 접근만 추상화 — 테스트에서 가짜 저장소를 넣고, 브라우저에서는 localStorage */
export type CartStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

// 회원별 키 — 같은 브라우저에서 계정을 바꿔도 장바구니가 섞이지 않음 (v1: 저장 형식이 바뀌면 올려 이전 값을 무시)
export function cartKey(userId: number): string {
  return `cart:v1:${userId}`;
}

function isLine(value: unknown): value is StoredCartLine {
  if (typeof value !== 'object' || value == null) return false;
  const { menu, quantity } = value as { menu?: Partial<Menu>; quantity?: unknown };
  return (
    typeof menu === 'object' &&
    menu != null &&
    typeof menu.id === 'number' &&
    typeof menu.storeId === 'number' &&
    typeof menu.price === 'number' &&
    typeof menu.name === 'string' &&
    typeof quantity === 'number' &&
    Number.isInteger(quantity) &&
    quantity > 0
  );
}

/** 저장된 장바구니 — 없거나 깨졌거나(형식 불일치) 저장소를 못 쓰면 빈 장바구니 */
export function loadCart(storage: CartStorage | null, userId: number | null): StoredCartLine[] {
  if (!storage || userId == null) return [];
  try {
    const raw = storage.getItem(cartKey(userId));
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed) || !parsed.every(isLine)) return [];
    // 한 장바구니는 한 매장 메뉴만 (주문 규칙) — 섞여 있으면 손댄 값이라 버림
    return parsed.every((l) => l.menu.storeId === parsed[0].menu.storeId) ? parsed : [];
  } catch {
    return [];
  }
}

/** 빈 장바구니는 키를 지움 (주문 완료·비우기 뒤 흔적을 남기지 않음) */
export function saveCart(storage: CartStorage | null, userId: number | null, lines: StoredCartLine[]): void {
  if (!storage || userId == null) return;
  try {
    if (lines.length === 0) storage.removeItem(cartKey(userId));
    else storage.setItem(cartKey(userId), JSON.stringify(lines));
  } catch {
    // 저장소가 가득 찼거나 막힌 환경 — 새로고침 시 장바구니가 비는 이전 동작으로 남음
  }
}

/** 브라우저 localStorage (사생활 보호 모드 등에서 접근 자체가 예외일 수 있음) */
export function browserCartStorage(): CartStorage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}
