import { useState } from 'react';
import { notificationApi } from '../api/endpoints';
import { DelayedSpinner } from '../components/DelayedSpinner';
import { ErrorMessage } from '../components/ErrorMessage';
import { formatDateTime } from '../components/format';
import { Pager } from '../components/Pager';
import { useAsync } from '../hooks/useAsync';

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(() => notificationApi.list(page), [page]);
  const [actionError, setActionError] = useState<unknown>(null);

  const markRead = async (id: number) => {
    try {
      // 서버가 멱등(이미 읽어도 204) → 중복 클릭을 막을 필요 없음
      await notificationApi.markRead(id);
      reload();
    } catch (err) {
      setActionError(err);
    }
  };

  return (
    <section>
      <h1>알림</h1>
      {/* 운영 구성은 Kafka, 무료 데모(prod-free)는 Kafka 없이 서버 안에서 바로 전달 — 어느 쪽이든 결제 저장 뒤 따로 만들어짐 */}
      <p className="muted">알림은 결제가 저장된 뒤 따로 만들어지므로, 결제 직후 몇 초 동안은 아직 없을 수 있습니다.</p>
      <ErrorMessage error={error ?? actionError} />
      {loading && <DelayedSpinner />}
      {data && (
        <>
          {data.items.length === 0 && <p className="notice">알림이 없습니다.</p>}
          <ul className="list">
            {data.items.map((n) => (
              <li key={n.id} className="card row" style={{ justifyContent: 'space-between' }}>
                <div>
                  {!n.read && <strong>● </strong>}
                  {n.message}
                  <div className="muted">{formatDateTime(n.createdAt)}</div>
                </div>
                {!n.read && (
                  <button type="button" className="btn btn--ghost" onClick={() => markRead(n.id)}>
                    읽음
                  </button>
                )}
              </li>
            ))}
          </ul>
          <Pager page={page} hasNext={data.hasNext} onChange={setPage} />
        </>
      )}
    </section>
  );
}
