import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createServerWake, isGatewayFailure } from './serverWake';

describe('서버 깨우기', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // 가짜 시계: sleep만큼 시간이 흐름
  function clock() {
    let t = 0;
    return {
      now: () => t,
      sleep: vi.fn(async (ms: number) => {
        t += ms;
      }),
    };
  }

  it('서버가 바로 응답하면 안내 없이 ready가 된다', async () => {
    const c = clock();
    const wake = createServerWake({ probe: async () => true, ...c });
    const states: string[] = [];
    wake.subscribe(() => states.push(wake.getState()));

    await expect(wake.ensureAwake()).resolves.toBe(true);

    expect(states).toEqual(['ready']);
    expect(c.sleep).not.toHaveBeenCalled();
  });

  it('응답이 느리면 waking으로 안내하고 응답이 오면 ready가 된다', async () => {
    let answer!: (ok: boolean) => void;
    const wake = createServerWake({ probe: () => new Promise<boolean>((r) => (answer = r)), slowAfterMs: 2000 });

    const result = wake.ensureAwake();
    expect(wake.getState()).toBe('idle');
    await vi.advanceTimersByTimeAsync(2000);
    expect(wake.getState()).toBe('waking');

    answer(true);
    await expect(result).resolves.toBe(true);
    expect(wake.getState()).toBe('ready');
  });

  it('실패하면 간격을 늘려 가며 다시 확인하고 성공하면 ready가 된다', async () => {
    const c = clock();
    const probe = vi
      .fn<() => Promise<boolean>>()
      .mockResolvedValueOnce(false)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
    const wake = createServerWake({ probe, retryDelaysMs: [1000, 2000], ...c });

    await expect(wake.ensureAwake()).resolves.toBe(true);

    expect(probe).toHaveBeenCalledTimes(4);
    expect(c.sleep.mock.calls.map(([ms]) => ms)).toEqual([1000, 2000, 2000]);
    expect(wake.getState()).toBe('ready');
  });

  it('제한 시간이 지나도 응답이 없으면 failed가 되고 다시 호출하면 처음부터 확인한다', async () => {
    const c = clock();
    const probe = vi.fn<() => Promise<boolean>>().mockResolvedValue(false);
    const wake = createServerWake({ probe, retryDelaysMs: [1000], giveUpAfterMs: 3000, ...c });

    await expect(wake.ensureAwake()).resolves.toBe(false);
    expect(wake.getState()).toBe('failed');

    probe.mockResolvedValue(true);
    await expect(wake.ensureAwake()).resolves.toBe(true);
    expect(wake.getState()).toBe('ready');
  });

  it('동시에 여러 번 불러도 확인은 하나만 하고 같은 결과를 기다린다', async () => {
    let answer!: (ok: boolean) => void;
    const probe = vi.fn(() => new Promise<boolean>((r) => (answer = r)));
    const wake = createServerWake({ probe });

    const a = wake.ensureAwake();
    const b = wake.ensureAwake();
    const settled = wake.whenSettled();
    answer(true);

    await expect(Promise.all([a, b, settled])).resolves.toEqual([true, true, undefined]);
    expect(probe).toHaveBeenCalledTimes(1);
  });

  it('게이트웨이 오류는 502와 504만이다', () => {
    expect([502, 504].every(isGatewayFailure)).toBe(true);
    expect([200, 401, 500, 503].some(isGatewayFailure)).toBe(false);
  });
});
