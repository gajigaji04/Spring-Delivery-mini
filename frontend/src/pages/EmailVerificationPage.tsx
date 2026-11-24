import { useState } from 'react';
import { authApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useFragmentToken } from '../auth/useFragmentToken';
import { AuthShell } from '../components/AuthShell';
import { ErrorMessage } from '../components/ErrorMessage';
import { PrefetchLink } from '../components/PrefetchLink';

/**
 * 이메일 인증 (V24) — 인증 메일 링크(/email-verification#token=...)로 들어옴
 * - 토큰 형식·처리는 비밀번호 재설정 링크와 같아 같은 훅(useFragmentToken)으로 읽음
 * - 화면을 열자마자 인증하지 않고 버튼을 눌러야 인증: 메일 보안 검사기가 링크를 미리 열어도 토큰이 쓰이지 않도록
 * - 로그인 없이 동작 (메일을 다른 기기에서 열 수 있음). 로그인 상태면 성공 후 내 정보를 다시 읽음
 */
export default function EmailVerificationPage() {
  const { me, reloadMe } = useAuth();
  // 조각 토큰은 화면 상태로 옮긴 뒤 주소창에서 지움 (비밀번호 재설정과 같은 훅)
  const { token } = useFragmentToken();
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const onVerify = async () => {
    if (!token) return;
    setSubmitting(true);
    setError(null);
    try {
      await authApi.confirmEmailVerification(token);
      setDone(true);
      if (me) await reloadMe().catch(() => undefined);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const next = me ? (
    <PrefetchLink to="/me" page="me" className="link">
      내 정보로
    </PrefetchLink>
  ) : (
    <PrefetchLink to="/login" page="login" className="link">
      로그인
    </PrefetchLink>
  );

  return (
    <AuthShell topRight={next}>
      <div className="lpanel__head">
        <h2>이메일 인증</h2>
        <p>인증을 마치면 이 주소로 비밀번호를 찾을 수 있어요.</p>
      </div>
      {!token ? (
        <div className="card" role="alert">
          <p>인증 링크가 올바르지 않아요. 메일의 링크를 그대로 열었는지 확인하거나 내 정보에서 인증 메일을 다시 요청해 주세요.</p>
        </div>
      ) : done ? (
        <div className="card" role="status">
          <p>이메일 인증을 마쳤어요.</p>
          <p>{next}</p>
        </div>
      ) : (
        <>
          <ErrorMessage error={error} />
          <button type="button" className="btn btn--lg btn--block" onClick={onVerify} disabled={submitting}>
            {submitting ? '인증하는 중…' : '이메일 인증하기'}
          </button>
        </>
      )}
    </AuthShell>
  );
}
