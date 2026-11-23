// 서버 깨우기 — 무료 호스팅(Render 무료 웹 서비스)은 한동안 요청이 없으면 서버를 재우고, 다음 요청 때 다시 띄움
// (0.1 CPU 기준 로컬 측정으로 기동에 수 분, docs/operations.md 9장). 그동안 화면이 오류로 끝나지 않도록
// - 가벼운 공개 GET(probe)으로 서버가 응답하는지 확인 → 응답이 오면 ready
// - 응답이 slowAfterMs보다 늦거나 실패하면 waking(안내 배너 표시) 상태로 바꾸고 간격을 늘려 가며 다시 확인
// - giveUpAfterMs가 지나도 안 되면 failed(새로고침 안내). 다시 호출하면 처음부터 확인
// - 확인은 동시에 하나만(single-flight) — 여러 요청이 같은 확인을 기다림
// probe를 주입받는 순수 로직이라 테스트에서 fetch·타이머 없이 검증 (client.ts가 실제 probe로 하나 만들어 씀)

export type WakeState = 'idle' | 'waking' | 'ready' | 'failed';

export interface ServerWakeOptions {
  /** 서버가 응답하면 true, 연결 실패·게이트웨이 오류(502·504)면 false */
  probe: () => Promise<boolean>;
  /** 이 시간 안에 응답이 없으면 안내 배너 표시 — 깨어 있는 서버에서 배너가 깜빡이지 않도록 */
  slowAfterMs?: number;
  /** 다시 확인하기 전 대기 시간 (마지막 값을 계속 사용) */
  retryDelaysMs?: number[];
  /** 이 시간이 지나도 응답이 없으면 포기 */
  giveUpAfterMs?: number;
  sleep?: (ms: number) => Promise<void>;
  now?: () => number;
}

export interface ServerWake {
  getState: () => WakeState;
  subscribe: (listener: () => void) => () => void;
  /** 서버가 응답할 때까지 확인 (이미 확인 중이면 그 결과를 같이 기다림) */
  ensureAwake: () => Promise<boolean>;
  /** 확인 중이면 끝날 때까지 기다림, 아니면 바로 반환 — API 요청 앞에서 호출 */
  whenSettled: () => Promise<void>;
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export function createServerWake({
  probe,
  slowAfterMs = 2000,
  retryDelaysMs = [2000, 3000, 5000, 10000],
  // 로컬 측정(0.1 CPU) 최악 약 5분 + 여유
  giveUpAfterMs = 6 * 60 * 1000,
  sleep = defaultSleep,
  now = Date.now,
}: ServerWakeOptions): ServerWake {
  let state: WakeState = 'idle';
  let running: Promise<boolean> | null = null;
  const listeners = new Set<() => void>();

  const setState = (next: WakeState) => {
    if (state === next) return;
    state = next;
    listeners.forEach((l) => l());
  };

  const safeProbe = () => probe().catch(() => false);

  async function run(): Promise<boolean> {
    const startedAt = now();
    const slowTimer = setTimeout(() => setState('waking'), slowAfterMs);
    try {
      for (let attempt = 0; ; attempt++) {
        if (await safeProbe()) {
          setState('ready');
          return true;
        }
        setState('waking');
        if (now() - startedAt >= giveUpAfterMs) {
          setState('failed');
          return false;
        }
        await sleep(retryDelaysMs[Math.min(attempt, retryDelaysMs.length - 1)]);
      }
    } finally {
      clearTimeout(slowTimer);
    }
  }

  return {
    getState: () => state,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    ensureAwake: () => {
      if (!running) {
        running = run().finally(() => {
          running = null;
        });
      }
      return running;
    },
    whenSettled: async () => {
      if (running) await running;
    },
  };
}

/** 서버가 자는 중일 때 보이는 응답 — 연결 실패는 fetch 예외, 게이트웨이(Vercel 프록시 등)의 502·504 */
export function isGatewayFailure(status: number): boolean {
  return status === 502 || status === 504;
}
