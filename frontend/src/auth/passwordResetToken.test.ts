import { describe, expect, it } from 'vitest';
import { readFragmentToken, readResetToken } from './passwordResetToken';

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

describe('readFragmentToken', () => {
  it('형식이 맞으면 토큰, 깨진 링크 아님', () => {
    expect(readFragmentToken(`#token=${TOKEN}`)).toEqual({ token: TOKEN, invalid: false });
  });

  it('조각이 없으면(직접 들어옴) 깨진 링크로 보지 않는다', () => {
    expect(readFragmentToken('')).toEqual({ token: null, invalid: false });
    expect(readFragmentToken('#other=1')).toEqual({ token: null, invalid: false });
  });

  it('token=이 있는데 형식이 틀리면 깨진 링크 — 메일 앱이 링크를 자른 경우 안내', () => {
    expect(readFragmentToken('#token=')).toEqual({ token: null, invalid: true });
    expect(readFragmentToken(`#token=${TOKEN.slice(0, 20)}`)).toEqual({ token: null, invalid: true });
    expect(readFragmentToken(`#token=${TOKEN}%20`)).toEqual({ token: null, invalid: true });
  });

  it('같은 조각을 여러 번 읽어도 결과가 같다 — 렌더가 다시 시작돼도 토큰 유지 (읽기에 부수효과 없음)', () => {
    const hash = `#token=${TOKEN}`;
    expect(readFragmentToken(hash)).toEqual(readFragmentToken(hash));
  });
});
