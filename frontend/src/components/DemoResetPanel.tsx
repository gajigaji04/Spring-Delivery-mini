import { useState } from 'react';
import { adminApi } from '../api/endpoints';
import { loadDemoAccounts } from '../auth/demoAccounts';
import { useAsync } from '../hooks/useAsync';
import { ErrorMessage } from './ErrorMessage';

/**
 * 데모 데이터 초기화 (관리자) — 데모 배포에서만 보임 (체험 계정 목록이 있을 때)
 * 앞 방문자가 확정·지급·승인한 상태를 처음으로 되돌림: 지난 3일 정산(지급·확정·대기), 승인 대기 신청, 오늘 주문
 * 데모 회원은 남으므로 지금 로그인은 그대로. onDone으로 화면 목록을 다시 불러옴
 */
export function DemoResetPanel({ onDone }: { onDone: () => void }) {
  const { data: demoAccounts } = useAsync(loadDemoAccounts, []);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);
  if (!demoAccounts || demoAccounts.length === 0) return null;

  const reset = async () => {
    if (!window.confirm('데모 매장의 정산·주문·사장님 전환 신청을 처음 상태로 되돌릴까요?')) return;
    setBusy(true);
    setError(null);
    setDone(false);
    try {
      await adminApi.resetDemoData();
      setDone(true);
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="row" style={{ alignItems: 'center', flexWrap: 'wrap', gap: 8, margin: '8px 0 16px' }}>
      <span className="muted" style={{ fontSize: 13 }}>
        데모 정산은 어제까지 3일치(지급·확정·대기)입니다.
        {done && ' 초기화했습니다.'}
      </span>
      <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={() => void reset()}>
        {busy ? '초기화 중…' : '데모 데이터 초기화'}
      </button>
      <ErrorMessage error={error} />
    </div>
  );
}
