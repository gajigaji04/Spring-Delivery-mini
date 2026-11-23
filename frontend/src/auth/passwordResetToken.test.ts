import { describe, expect, it } from 'vitest';
import { readResetToken } from './passwordResetToken';

const TOKEN = 'a'.repeat(40) + '_-Z';

describe('readResetToken', () => {
  it('#token= 조각에서 토큰을 읽는다', () => {
    expect(readResetToken(`#token=${TOKEN}`)).toBe(TOKEN);
    expect(readResetToken(`token=${TOKEN}`)).toBe(TOKEN);
  });

  it('없거나 형식이 다르면 null — 링크 요청 단계로 시작', () => {
    expect(readResetToken('')).toBeNull();
    expect(readResetToken('#token=')).toBeNull();
    expect(readResetToken('#token=short')).toBeNull();
    expect(readResetToken(`#token=${TOKEN}<script>`)).toBeNull();
  });
});
