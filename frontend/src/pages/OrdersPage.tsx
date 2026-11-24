import { useEffect, useState } from 'react';
import { orderApi } from '../api/endpoints';
import type { Order } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DelayedSpinner } from '../components/DelayedSpinner';
import { ErrorMessage } from '../components/ErrorMessage';
import { formatDateTime, Money, orderStatusLabel, StatusBadge } from '../components/format';
import { PrefetchLink } from '../components/PrefetchLink';
import { usePolling } from '../hooks/usePolling';
import { isAwaitingStoreUpdate, ORDER_POLL_MS } from '../orders/orderLive';

/** keyset "더 보기" — 넘기는 중 새 주문이 들어와도 이미 본 주문이 다시 나오지 않음 */
export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [afterId, setAfterId] = useState<number | null>(null);
  const [nextAfterId, setNextAfterId] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    orderApi
      .list(afterId)
      .then((r) => {
        if (!active) return;
        setOrders((prev) => (afterId === null ? r.items : [...prev, ...r.items]));
        setNextAfterId(r.hasNext ? r.nextAfterId : null);
      })
      .catch((e) => active && setError(e))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [afterId]);

  // 고객: 사장님 처리를 기다리는 주문이 있으면 첫 페이지를 다시 받아 이미 보이는 주문의 상태만 바꿈 (orders/orderLive)
  //   처리 중인 주문은 가장 최근 주문이라 첫 페이지에 있음. 목록 구성(더 보기로 받은 주문)은 그대로 둠
  const { me } = useAuth();
  usePolling(me?.role === 'CUSTOMER' && orders.some((o) => isAwaitingStoreUpdate(o.status)), ORDER_POLL_MS, () => {
    orderApi
      .list(null)
      .then((r) => {
        const latest = new Map(r.items.map((o) => [o.id, o]));
        setOrders((prev) => prev.map((o) => latest.get(o.id) ?? o));
      })
      .catch(() => {
        // 자동 갱신 실패는 조용히 넘김 — 다음 주기에 다시 시도, 화면에는 마지막으로 받은 상태가 남음
      });
  });

  return (
    <section>
      <h1>주문</h1>
      <ErrorMessage error={error} />
      {!loading && orders.length === 0 && <p className="notice">주문이 없습니다.</p>}
      <ul className="list">
        {orders.map((o) => (
          <li key={o.id} className="card">
            <PrefetchLink to={`/orders/${o.id}`} page="orderDetail">
              #{o.id} {o.storeName}
            </PrefetchLink>{' '}
            <StatusBadge label={orderStatusLabel[o.status]} /> <Money value={o.totalPrice} />
            <div className="muted">{formatDateTime(o.createdAt)}</div>
          </li>
        ))}
      </ul>
      {loading && <DelayedSpinner />}
      {nextAfterId !== null && !loading && (
        <button type="button" className="btn btn--ghost" onClick={() => setAfterId(nextAfterId)}>
          더 보기
        </button>
      )}
    </section>
  );
}
