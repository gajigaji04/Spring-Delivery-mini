import { describe, expect, it } from 'vitest';
import { isDemoAdmin, REJECT_REASON_MAX, rejectReasonError } from './ownerApplications';

describe('rejectReasonError', () => {
  it('비어 있거나 공백뿐이면 입력 안내를 돌려준다', () => {
    expect(rejectReasonError('')).toContain('입력해 주세요');
    expect(rejectReasonError('   \n ')).toContain('입력해 주세요');
  });

  it('앞뒤 공백을 뺀 길이가 한도를 넘으면 길이 안내를 돌려준다', () => {
    expect(rejectReasonError('가'.repeat(REJECT_REASON_MAX + 1))).toContain(`${REJECT_REASON_MAX}자`);
    expect(rejectReasonError(`  ${'가'.repeat(REJECT_REASON_MAX)}  `)).toBeNull();
  });

  it('사유가 있으면 통과한다', () => {
    expect(rejectReasonError(' 상호 확인 불가 ')).toBeNull();
  });
});

describe('isDemoAdmin', () => {
  const accounts = [
    { role: 'CUSTOMER' as const, username: 'democustomer', password: 'pw' },
    { role: 'ADMIN' as const, username: 'demoadmin', password: null },
  ];

  it('체험 계정 목록의 관리자 아이디와 같으면 데모 관리자다', () => {
    expect(isDemoAdmin('demoadmin', accounts)).toBe(true);
  });

  it('다른 아이디이거나 관리자가 아닌 체험 계정이면 데모 관리자가 아니다', () => {
    expect(isDemoAdmin('adm6118974', accounts)).toBe(false);
    expect(isDemoAdmin('democustomer', accounts)).toBe(false);
  });

  it('체험 계정 목록이 없으면(데모가 꺼진 배포) 데모 관리자가 아니다', () => {
    expect(isDemoAdmin('demoadmin', [])).toBe(false);
    expect(isDemoAdmin('demoadmin', null)).toBe(false);
    expect(isDemoAdmin(undefined, accounts)).toBe(false);
  });
});
