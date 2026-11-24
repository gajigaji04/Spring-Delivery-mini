import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import type { Menu } from '../api/types';
import { checkCartMenus } from './cartValidation';

function menu(id: number, storeId = 1, price = 1000): Menu {
  return { id, storeId, name: `메뉴${id}`, price, description: null, createdAt: '', updatedAt: '' };
}

const notFound = () => new ApiError(404, '메뉴가 없습니다.', 'MENU_NOT_FOUND', null);

describe('checkCartMenus', () => {
  it('모두 판매 중이면 판매 종료 없음, 최신 정보를 돌려줌', async () => {
    const r = await checkCartMenus([{ menu: menu(1) }, { menu: menu(2) }], async (id) => menu(id, 1, 2000));
    expect(r.staleIds).toEqual([]);
    expect(r.current.get(1)?.price).toBe(2000);
  });

  it('404(삭제·데모 초기화로 사라진 메뉴)는 판매 종료', async () => {
    const r = await checkCartMenus([{ menu: menu(1) }, { menu: menu(2) }, { menu: menu(3) }], async (id) => {
      if (id !== 2) throw notFound();
      return menu(2);
    });
    expect(r.staleIds).toEqual([1, 3]);
  });

  it('같은 id가 다른 매장 메뉴면 판매 종료로 봄', async () => {
    const r = await checkCartMenus([{ menu: menu(1, 1) }], async () => menu(1, 9));
    expect(r.staleIds).toEqual([1]);
  });

  it('네트워크 오류·5xx는 판단 보류 — 주문을 막지 않음', async () => {
    const r = await checkCartMenus([{ menu: menu(1) }, { menu: menu(2) }], async (id) => {
      if (id === 1) throw new TypeError('Failed to fetch');
      throw new ApiError(503, '잠시 후', null, null);
    });
    expect(r.staleIds).toEqual([]);
  });

  it('결과 순서는 응답 도착 순서가 아니라 장바구니 순서', async () => {
    const r = await checkCartMenus([{ menu: menu(5) }, { menu: menu(3) }], (id) =>
      new Promise((_, reject) => setTimeout(() => reject(notFound()), id === 5 ? 20 : 0)),
    );
    expect(r.staleIds).toEqual([5, 3]);
  });
});
