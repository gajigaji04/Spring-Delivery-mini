import { useSyncExternalStore } from 'react';
import { serverWake } from '../api/client';

/**
 * 서버가 절전에서 깨어나는 동안 화면 위에 띄우는 안내 (api/serverWake.ts)
 * 깨어 있는 서버에서는 확인 응답이 2초 안에 와서 보이지 않음
 */
export function ServerWakeBanner() {
  const state = useSyncExternalStore(serverWake.subscribe, serverWake.getState);
  if (state === 'waking') {
    return (
      <div className="wake-banner" role="status" aria-live="polite">
        <span className="wake-banner__dot" aria-hidden="true" />
        서버를 깨우는 중이에요. 무료 서버라 한동안 접속이 없으면 잠들어서, 첫 접속은 몇 분 걸릴 수 있어요.
      </div>
    );
  }
  if (state === 'failed') {
    return (
      <div className="wake-banner wake-banner--failed" role="alert">
        서버에 연결하지 못했어요. 잠시 후 새로고침해 주세요.
        <button type="button" className="btn btn--white" onClick={() => location.reload()}>
          새로고침
        </button>
      </div>
    );
  }
  return null;
}
