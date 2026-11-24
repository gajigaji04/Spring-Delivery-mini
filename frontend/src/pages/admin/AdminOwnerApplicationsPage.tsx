import { Fragment, useState, type FormEvent } from 'react';
import { isDemoAdmin, REJECT_REASON_MAX, rejectReasonError } from '../../admin/ownerApplications';
import { adminApi } from '../../api/endpoints';
import type { OwnerApplicationStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { loadDemoAccounts } from '../../auth/demoAccounts';
import { DelayedSpinner } from '../../components/DelayedSpinner';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../components/format';
import { Pager } from '../../components/Pager';
import { useAsync } from '../../hooks/useAsync';

/** 사장님 전환 신청 승인·거절 (V17). 상태 전이 판단은 서버(이미 처리된 신청 409, 진행 중 주문 409) */
export default function AdminOwnerApplicationsPage() {
  const { me } = useAuth();
  const { data: demoAccounts } = useAsync(loadDemoAccounts, []);
  const [status, setStatus] = useState<OwnerApplicationStatus>('REQUESTED');
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(() => adminApi.ownerApplications(status, page), [status, page]);
  const [actionError, setActionError] = useState<unknown>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  // 거절 사유를 입력 중인 신청 — 한 번에 한 건만 펼침
  // (예전 window.prompt는 브라우저가 대화상자를 막으면 창 없이 바로 취소되어 "눌러도 반응 없음"으로 보였음)
  const [rejecting, setRejecting] = useState<{ id: number; reason: string; touched: boolean } | null>(null);

  const act = async (id: number, action: () => Promise<unknown>, { reloadList = true } = {}) => {
    setBusyId(id);
    setActionError(null);
    try {
      await action();
      if (reloadList) reload();
      return true;
    } catch (err) {
      setActionError(err);
      return false;
    } finally {
      setBusyId(null);
    }
  };

  const onSubmitReject = async (e: FormEvent) => {
    e.preventDefault();
    if (!rejecting) return;
    if (rejectReasonError(rejecting.reason)) {
      setRejecting({ ...rejecting, touched: true });
      return;
    }
    const { id, reason } = rejecting;
    // 실패하면 입력한 사유를 남겨 다시 시도할 수 있게 함
    // 성공하면 거절됨 탭으로 옮겨 방금 처리한 신청(사유·처리자)을 바로 보여 줌 — 승인 대기에 남으면 빈 목록만 보여 "처리됐나?"가 됨
    // 탭이 바뀌면 목록을 새로 읽으므로 reload는 생략 (같은 요청 두 번 방지)
    if (await act(id, () => adminApi.rejectOwner(id, reason.trim()), { reloadList: false })) {
      setRejecting(null);
      setPage(0);
      setStatus('REJECTED');
    }
  };

  const reasonError = rejecting ? rejectReasonError(rejecting.reason) : null;

  return (
    <section>
      <h1>사장님 전환 신청</h1>
      {isDemoAdmin(me?.username, demoAccounts) && (
        <p className="notice">
          체험 관리자는 데모 회원의 신청만 볼 수 있습니다. 다른 회원이 낸 신청은 목록에 나오지 않으니 일반 관리자 계정으로 확인해 주세요.
        </p>
      )}
      <select
        value={status}
        onChange={(e) => {
          setStatus(e.target.value as OwnerApplicationStatus);
          setPage(0);
          setRejecting(null);
        }}
      >
        <option value="REQUESTED">승인 대기</option>
        <option value="APPROVED">승인됨</option>
        <option value="REJECTED">거절됨</option>
      </select>
      <ErrorMessage error={error ?? actionError} />
      {loading && <DelayedSpinner />}
      {data && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>id</th>
                  <th>아이디</th>
                  <th>상호</th>
                  <th>신청</th>
                  <th>처리</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((a) => (
                  <Fragment key={a.id}>
                    <tr>
                      <td className="num">{a.id}</td>
                      <td>{a.username}</td>
                      <td>{a.businessName}</td>
                      <td>{formatDateTime(a.requestedAt)}</td>
                      <td>
                        {a.status === 'REQUESTED' ? (
                          <span className="row">
                            <button
                              type="button"
                              className="btn"
                              disabled={busyId === a.id || rejecting?.id === a.id}
                              onClick={() => act(a.id, () => adminApi.approveOwner(a.id))}
                            >
                              승인
                            </button>
                            <button
                              type="button"
                              className="btn btn--ghost"
                              disabled={busyId === a.id || rejecting?.id === a.id}
                              onClick={() => setRejecting({ id: a.id, reason: '', touched: false })}
                            >
                              거절
                            </button>
                          </span>
                        ) : (
                          <span className="muted">
                            {a.decidedBy} · {formatDateTime(a.decidedAt)}
                            {a.rejectReason && ` · ${a.rejectReason}`}
                          </span>
                        )}
                      </td>
                    </tr>
                    {rejecting?.id === a.id && (
                      <tr>
                        <td colSpan={5}>
                          <form className="form" onSubmit={(e) => void onSubmitReject(e)}>
                            <label>
                              거절 사유 (신청자에게 그대로 보입니다)
                              <input
                                autoFocus
                                value={rejecting.reason}
                                maxLength={REJECT_REASON_MAX}
                                placeholder="예: 상호를 확인할 수 없습니다."
                                onChange={(e) => setRejecting({ ...rejecting, reason: e.target.value })}
                              />
                            </label>
                            {rejecting.touched && reasonError && <p className="muted">{reasonError}</p>}
                            <span className="row">
                              <button type="submit" className="btn btn--danger" disabled={busyId === a.id}>
                                {busyId === a.id ? '거절 중…' : '거절 확정'}
                              </button>
                              <button type="button" className="btn btn--ghost" disabled={busyId === a.id} onClick={() => setRejecting(null)}>
                                취소
                              </button>
                            </span>
                          </form>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
          {data.items.length === 0 && <p className="notice">신청이 없습니다.</p>}
          <Pager page={page} hasNext={data.hasNext} onChange={setPage} />
        </>
      )}
    </section>
  );
}
