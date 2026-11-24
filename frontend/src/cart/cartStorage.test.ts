import { describe, expect, it } from 'vitest';
import type { Menu } from '../api/types';
import { cartKey, loadCart, saveCart, type CartStorage } from './cartStorage';

function memoryStorage(): CartStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => void data.set(k, v),
    removeItem: (k) => void data.delete(k),
  };
}

const menu = (id: number, storeId = 1): Menu => ({
  id,
  storeId,
  name: `메뉴${id}`,
  price: 1000 * id,
  description: null,
  createdAt: '2026-11-24T10:00:00',
  updatedAt: '2026-11-24T10:00:00',
});

describe('cartStorage', () => {
  it('새로고침 뒤에도 같은 회원의 장바구니를 그대로 읽는다', () => {
    const storage = memoryStorage();
    saveCart(storage, 7, [{ menu: menu(1), quantity: 2 }]);
    expect(loadCart(storage, 7)).toEqual([{ menu: menu(1), quantity: 2 }]);
  });

  it('다른 회원의 장바구니는 보이지 않는다', () => {
    const storage = memoryStorage();
    saveCart(storage, 7, [{ menu: menu(1), quantity: 2 }]);
    expect(loadCart(storage, 8)).toEqual([]);
    expect(loadCart(storage, null)).toEqual([]);
  });

  it('비우면 키를 지우고, 로그인 전(회원 없음)에는 저장하지 않는다', () => {
    const storage = memoryStorage();
    saveCart(storage, 7, [{ menu: menu(1), quantity: 1 }]);
    saveCart(storage, 7, []);
    expect(storage.data.has(cartKey(7))).toBe(false);
    saveCart(storage, null, [{ menu: menu(1), quantity: 1 }]);
    expect(storage.data.size).toBe(0);
  });

  it('깨진 값·형식이 다른 값·여러 매장이 섞인 값은 빈 장바구니로 읽는다', () => {
    const storage = memoryStorage();
    storage.setItem(cartKey(7), '{not json');
    expect(loadCart(storage, 7)).toEqual([]);
    storage.setItem(cartKey(7), JSON.stringify([{ menu: { id: 1 }, quantity: 1 }]));
    expect(loadCart(storage, 7)).toEqual([]);
    storage.setItem(cartKey(7), JSON.stringify([{ menu: menu(1), quantity: 0 }]));
    expect(loadCart(storage, 7)).toEqual([]);
    storage.setItem(cartKey(7), JSON.stringify([{ menu: menu(1, 1), quantity: 1 }, { menu: menu(2, 2), quantity: 1 }]));
    expect(loadCart(storage, 7)).toEqual([]);
  });

  it('저장소를 쓸 수 없으면 조용히 빈 장바구니', () => {
    const broken: CartStorage = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {},
    };
    expect(loadCart(broken, 7)).toEqual([]);
    expect(() => saveCart(broken, 7, [{ menu: menu(1), quantity: 1 }])).not.toThrow();
    expect(loadCart(null, 7)).toEqual([]);
  });
});
