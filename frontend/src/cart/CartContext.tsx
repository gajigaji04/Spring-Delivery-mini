import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Menu } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { browserCartStorage, loadCart, saveCart } from './cartStorage';

export interface CartLine {
  menu: Menu;
  quantity: number;
}

interface CartContextValue {
  lines: CartLine[];
  storeId: number | null;
  total: number;
  add: (menu: Menu) => void;
  setQuantity: (menuId: number, quantity: number) => void;
  /** 판매 종료 메뉴 빼기 + 남은 메뉴의 이름·가격을 서버 최신 값으로 (cart/cartValidation 결과) */
  applyCheck: (staleIds: number[], current: Map<number, Menu>) => void;
  clear: () => void;
}

const CartContext = createContext<CartContextValue | null>(null);

/**
 * 장바구니는 회원별로 localStorage에 둠 (cartStorage) — 새로고침해도 유지
 * - 회원 id가 바뀌면(로그인·로그아웃·계정 전환) 그 회원의 장바구니로 바꿔 읽음 → 다른 회원 것과 섞이지 않음
 * - 로그아웃하면 화면에서는 비지만 저장 값은 남아, 같은 회원이 다시 로그인하면 이어서 담을 수 있음
 * - 주문이 끝나면 clear()로 비우고 저장 키도 지움. 가격은 표시용이라 오래된 값이어도 주문 금액은 서버가 다시 계산
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  const userId = me?.id ?? null;
  const [cart, setCart] = useState<{ userId: number | null; lines: CartLine[] }>(() => ({
    userId,
    lines: loadCart(browserCartStorage(), userId),
  }));

  // 회원이 바뀐 첫 렌더에서 바로 그 회원의 장바구니로 교체 (effect로 바꾸면 이전 회원 장바구니가 한 번 그려지고 새 키에 저장될 수 있음)
  let current = cart;
  if (cart.userId !== userId) {
    current = { userId, lines: loadCart(browserCartStorage(), userId) };
    setCart(current);
  }
  const lines = current.lines;

  useEffect(() => {
    saveCart(browserCartStorage(), cart.userId, cart.lines);
  }, [cart]);

  const setLines = useCallback((update: (prev: CartLine[]) => CartLine[]) => {
    setCart((prev) => ({ ...prev, lines: update(prev.lines) }));
  }, []);

  const add = useCallback((menu: Menu) => {
    setLines((prev) => {
      // 주문은 한 매장의 메뉴만 가능(다르면 서버 400) → 다른 매장 메뉴를 담으면 장바구니를 새로 시작
      const base = prev.length > 0 && prev[0].menu.storeId !== menu.storeId ? [] : prev;
      const found = base.find((l) => l.menu.id === menu.id);
      // 같은 메뉴는 한 항목으로 합침 (중복 menuId도 서버 400)
      if (found) return base.map((l) => (l.menu.id === menu.id ? { ...l, quantity: l.quantity + 1 } : l));
      return [...base, { menu, quantity: 1 }];
    });
  }, [setLines]);

  const setQuantity = useCallback((menuId: number, quantity: number) => {
    setLines((prev) =>
      quantity <= 0 ? prev.filter((l) => l.menu.id !== menuId) : prev.map((l) => (l.menu.id === menuId ? { ...l, quantity } : l)),
    );
  }, [setLines]);

  const applyCheck = useCallback((staleIds: number[], current: Map<number, Menu>) => {
    setLines((prev) =>
      prev.filter((l) => !staleIds.includes(l.menu.id)).map((l) => ({ ...l, menu: current.get(l.menu.id) ?? l.menu })),
    );
  }, [setLines]);

  const clear = useCallback(() => setLines(() => []), [setLines]);

  const value = useMemo(() => {
    // 화면 표시용 합계 — 실제 결제 금액은 서버가 주문 당시 메뉴 가격으로 다시 계산
    const total = lines.reduce((sum, l) => sum + l.menu.price * l.quantity, 0);
    return { lines, storeId: lines[0]?.menu.storeId ?? null, total, add, setQuantity, applyCheck, clear };
  }, [lines, add, setQuantity, applyCheck, clear]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('CartProvider 밖에서 useCart를 사용했습니다');
  return ctx;
}
