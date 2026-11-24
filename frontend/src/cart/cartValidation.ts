import { ApiError } from '../api/client';
import type { Menu } from '../api/types';

export interface CartCheck {
  /** 지금은 주문할 수 없는 메뉴 id — 삭제됐거나(404) 다른 매장 메뉴로 바뀜 */
  staleIds: number[];
  /** 서버 최신 메뉴 정보 (이름·가격 표시 갱신용) */
  current: Map<number, Menu>;
}

/**
 * 장바구니에 담긴 메뉴가 아직 판매 중인지 서버에 다시 확인.
 *
 * 장바구니는 브라우저 localStorage에 있어 서버 쪽 변경(사장님 메뉴 삭제, 데모 데이터 초기화로 메뉴 id 교체)을 모름.
 * 그대로 주문하면 서버가 404 MENU_NOT_FOUND로 막지만(정상) 어떤 메뉴가 문제인지 알 수 없으므로 화면에서 먼저 짚어 줌.
 * - 404만 판매 종료로 봄. 네트워크 오류·5xx는 "모름" → 막지 않음 (최종 판단은 어차피 주문 API)
 */
export async function checkCartMenus(
  lines: { menu: Pick<Menu, 'id' | 'storeId'> }[],
  fetchMenu: (id: number) => Promise<Menu>,
): Promise<CartCheck> {
  const staleIds: number[] = [];
  const current = new Map<number, Menu>();
  await Promise.all(
    lines.map(async ({ menu }) => {
      try {
        const latest = await fetchMenu(menu.id);
        // 같은 id가 다른 매장 메뉴로 쓰이는 경우(초기화 뒤 id 재사용)도 담은 메뉴가 아님
        if (latest.storeId !== menu.storeId) staleIds.push(menu.id);
        else current.set(menu.id, latest);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) staleIds.push(menu.id);
      }
    }),
  );
  // 화면 표시 순서가 응답 도착 순서에 흔들리지 않도록 장바구니 순서로
  const order = new Map(lines.map((l, i) => [l.menu.id, i]));
  staleIds.sort((a, b) => (order.get(a) ?? 0) - (order.get(b) ?? 0));
  return { staleIds, current };
}
