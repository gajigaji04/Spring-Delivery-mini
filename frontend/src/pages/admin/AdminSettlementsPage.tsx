import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../../api/endpoints';
import type { SettlementStatus } from '../../api/types';
import { DelayedSpinner } from '../../components/DelayedSpinner';
import { DemoResetPanel } from '../../components/DemoResetPanel';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Money, settlementStatusLabel, StatusBadge } from '../../components/format';
import { useAsync } from '../../hooks/useAsync';

/** 어제(Asia/Seoul) — 정산이 하나도 없거나 최근 날짜 조회가 실패했을 때의 기본값 (정산은 D+1 01:10에 전날분 집계) */
function yesterdayInSeoul(): string {
  const d = new Date(Date.now() - 24 * 60 * 60 * 1000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(d); // yyyy-MM-dd
}

export default function AdminSettlementsPage() {
  // 기본 날짜 = 정산이 있는 가장 최근 날짜 (서버 조회, 하드코딩 없음) → 데이터가 없는 어제로 열려 빈 목록만 보이던 문제
  //   (배포 데모는 정산이 데모 매장 생성일 이전 3일에만 있음). 정해지기 전(null)에는 목록을 조회하지 않음
  const [date, setDate] = useState<string | null>(null);
  const [status, setStatus] = useState<SettlementStatus | ''>('');
  const { data, error, loading, reload } = useAsync(
    () => (date == null ? Promise.resolve(undefined) : adminApi.settlements(date, status || undefined)),
    [date, status],
  );

  const resetToLatestDate = useCallback(() => {
    adminApi
      .latestSettlementDate()
      .then((latest) => setDate(latest ?? yesterdayInSeoul()))
      .catch(() => setDate((prev) => prev ?? yesterdayInSeoul()));
  }, []);
  useEffect(resetToLatestDate, [resetToLatestDate]);
  const [actionError, setActionError] = useState<unknown>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const act = async (id: number, action: (id: number) => Promise<unknown>) => {
    setBusyId(id);
    setActionError(null);
    try {
      await action(id);
      reload();
    } catch (err) {
      setActionError(err);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section>
      <h1>정산 관리</h1>
      {/* 초기화하면 정산 날짜가 바뀔 수 있어 최근 날짜를 다시 정함 (같은 날짜면 목록만 다시 조회) */}
      <DemoResetPanel
        onDone={() => {
          resetToLatestDate();
          reload();
        }}
      />
      <div className="row">
        <input type="date" value={date ?? ''} onChange={(e) => setDate(e.target.value)} required />
        <select value={status} onChange={(e) => setStatus(e.target.value as SettlementStatus | '')}>
          <option value="">전체 상태</option>
          {Object.entries(settlementStatusLabel).map(([v, label]) => (
            <option key={v} value={v}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <ErrorMessage error={error ?? actionError} />
      {loading && <DelayedSpinner />}
      {data && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>id</th>
                <th>매장</th>
                <th>상태</th>
                <th className="amount">지급액</th>
                <th>처리</th>
              </tr>
            </thead>
            <tbody>
              {data.map((s) => (
                <tr key={s.id}>
                  <td className="num">{s.id}</td>
                  <td>{s.storeName}</td>
                  <td><StatusBadge label={settlementStatusLabel[s.status]} /></td>
                  <td className="amount"><Money value={s.settlementAmount} /></td>
                  <td>
                    {/* 상태 전이는 서버가 판단(위반 409) — 버튼은 현재 상태에서 가능한 것만 보여줌 */}
                    {s.status === 'PENDING' && (
                      <button type="button" className="btn" disabled={busyId === s.id} onClick={() => act(s.id, adminApi.confirm)}>
                        확정
                      </button>
                    )}
                    {s.status === 'CONFIRMED' && (
                      <button type="button" className="btn" disabled={busyId === s.id} onClick={() => act(s.id, adminApi.pay)}>
                        지급
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.length === 0 && <p className="notice">이 날짜의 정산이 없습니다.</p>}
        </div>
      )}
    </section>
  );
}
