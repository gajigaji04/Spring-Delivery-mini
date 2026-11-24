import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/endpoints';
import { useFragmentToken } from '../auth/useFragmentToken';
import { AuthShell } from '../components/AuthShell';
import { ErrorMessage } from '../components/ErrorMessage';
import { PrefetchLink } from '../components/PrefetchLink';
import { useToast } from '../components/Toast';

/**
 * 비밀번호 찾기 (V23)
 * - 링크 없이 들어오면: 이메일 입력 → 재설정 링크 메일 요청 (가입 여부와 관계없이 같은 안내)
 * - 메일 링크(#token=)로 들어오면: 새 비밀번호 입력 → 재설정 → 로그인 화면
 */
export default function PasswordResetPage() {
  // 조각 토큰은 화면 상태로 옮긴 뒤 주소창에서 지움 (읽기·지우기 분리 이유는 useFragmentToken)
  const { token, invalid } = useFragmentToken();

  return (
    <AuthShell
      topRight={
        <PrefetchLink to="/login" page="login" className="link">
          로그인
        </PrefetchLink>
      }
    >
      {token ? <NewPasswordForm token={token} /> : <RequestLinkForm brokenLink={invalid} />}
    </AuthShell>
  );
}

function RequestLinkForm({ brokenLink }: { brokenLink: boolean }) {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await authApi.requestPasswordReset(email.trim());
      setSent(true);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <div className="lpanel__head">
        <h2>비밀번호 찾기</h2>
        <p>인증을 마친 이메일로 재설정 링크를 보내 드려요. 인증하지 않은 주소로는 메일이 가지 않아요.</p>
      </div>
      {/* 메일 앱이 링크를 자르거나 바꾼 경우 — 조용히 이메일 입력으로 보내지 않고 이유를 알려 줌 */}
      {brokenLink && !sent && (
        <div className="card" role="alert">
          <p>재설정 링크가 올바르지 않아요. 메일의 링크를 그대로 열었는지 확인하거나 아래에서 다시 요청해 주세요.</p>
        </div>
      )}
      {sent ? (
        // 가입되지 않은 주소에도 같은 안내 — 화면으로 가입 여부를 알 수 없게 (서버도 항상 202)
        <div className="card" role="status">
          <p>
            <b>{email.trim()}</b> 으로 인증된 계정이 있다면 재설정 링크를 보냈어요.
          </p>
          <p className="muted">링크는 30분 동안 한 번만 쓸 수 있어요. 메일이 없으면 스팸함을 확인하거나 1분 뒤 다시 요청해 주세요.</p>
          <button type="button" className="btn btn--ghost" onClick={() => setSent(false)}>
            다시 요청
          </button>
        </div>
      ) : (
        <form className="lform" onSubmit={onSubmit}>
          <div className="fld">
            <label htmlFor="reset-email">이메일</label>
            <input
              id="reset-email"
              className="inp"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setError(null);
              }}
              maxLength={255}
              required
            />
          </div>
          <ErrorMessage error={error} />
          <button type="submit" className="btn btn--lg btn--block" disabled={submitting}>
            {submitting ? '보내는 중…' : '재설정 링크 받기'}
          </button>
        </form>
      )}
    </>
  );
}

function NewPasswordForm({ token }: { token: string }) {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const mismatch = confirm.length > 0 && password !== confirm;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (mismatch) return;
    setSubmitting(true);
    setError(null);
    try {
      await authApi.confirmPasswordReset(token, password);
      // 서버가 이 계정의 모든 로그인을 끊음 → 새 비밀번호로 다시 로그인
      notify('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요');
      navigate('/login', { replace: true });
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  };

  return (
    <>
      <div className="lpanel__head">
        <h2>새 비밀번호 설정</h2>
        <p>바꾸면 모든 기기에서 로그아웃돼요.</p>
      </div>
      <form className="lform" onSubmit={onSubmit}>
        <div className="fld">
          <label htmlFor="reset-pw">새 비밀번호 (8~72자)</label>
          <input
            id="reset-pw"
            className="inp"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            maxLength={72}
            required
          />
        </div>
        <div className="fld">
          <label htmlFor="reset-pw2">새 비밀번호 확인</label>
          <input
            id="reset-pw2"
            className="inp"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            aria-invalid={mismatch}
            required
          />
          {mismatch && <p className="muted" role="alert">비밀번호가 서로 달라요.</p>}
        </div>
        <ErrorMessage error={error} />
        <button type="submit" className="btn btn--lg btn--block" disabled={submitting || mismatch}>
          {submitting ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </form>
      {error != null && (
        <p style={{ marginTop: 16 }}>
          <a className="link" href="/password-reset">
            비밀번호 찾기 다시 요청
          </a>
        </p>
      )}
    </>
  );
}
