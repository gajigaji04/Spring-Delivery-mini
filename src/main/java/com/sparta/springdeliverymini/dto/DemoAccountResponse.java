package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Role;

// 로그인 화면 체험 버튼 1개 — password는 공개 데모 비밀번호, 관리자는 null (POST /api/users/demo-login/admin 사용)
public record DemoAccountResponse(Role role, String username, String password) {
}
