import { useEffect, useRef } from 'react';

/**
 * active인 동안 intervalMs마다 tick 실행 — 탭이 숨겨져 있으면 건너뛰고, 다시 보이면 바로 한 번 실행
 * (백그라운드 탭이 무료 서버에 계속 요청하지 않도록. 화면으로 돌아왔을 때는 최신 상태를 바로 보여 줌)
 */
export function usePolling(active: boolean, intervalMs: number, tick: () => void): void {
  const tickRef = useRef(tick);
  tickRef.current = tick;

  useEffect(() => {
    if (!active) return;
    const run = () => {
      if (document.visibilityState === 'visible') tickRef.current();
    };
    const timer = window.setInterval(run, intervalMs);
    document.addEventListener('visibilitychange', run);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', run);
    };
  }, [active, intervalMs]);
}
