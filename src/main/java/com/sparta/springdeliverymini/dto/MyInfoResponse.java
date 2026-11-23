package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;

import java.time.LocalDateTime;
import java.util.List;

// 내 정보 조회 응답 (#62) — 토큰 값이 아니라 DB의 현재 회원 정보
// 토큰 발급 뒤 역할이 바뀌었으면 role은 바뀐 값 (화면 메뉴 구성은 이 값을 기준으로)
public record MyInfoResponse(
        Long id,
        String username,
        Role role,
        LocalDateTime createdAt, // 가입 시각 — 별도 표시 이름(닉네임) 컬럼이 없어 화면 표시는 username 사용
        List<Long> storeIds,     // OWNER의 매장 id (id 오름차순), 그 밖의 역할은 빈 배열
        String email,            // 비밀번호 찾기 메일 주소 (V23), 없으면 null
        boolean hasPassword,     // false면 소셜 전용 회원 — 비밀번호 변경 화면이 "현재 비밀번호" 없이 설정으로 바뀜
        boolean demoAccount,     // 공개 데모 계정 — 비밀번호·이메일 변경 불가 (화면에서 폼을 숨김)
        boolean emailVerified,   // email이 인증된 주소인지 (V24) — false면 비밀번호 찾기에 쓸 수 없음 (V24 이전 등록 주소)
        String pendingEmail) {   // 인증 메일을 보내고 링크를 기다리는 주소 (V24), 없으면 null — 인증 전까지 email은 그대로

    public static MyInfoResponse of(User user, List<Long> storeIds, boolean demoAccount, String pendingEmail) {
        return new MyInfoResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt(), storeIds,
                user.getEmail(), user.hasPassword(), demoAccount, user.isEmailVerified(), pendingEmail);
    }
}
