package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 변경 (V23) — PATCH /api/users/me/password
// currentPassword: 비밀번호가 있는 회원은 필수(틀리면 400 INVALID_CURRENT_PASSWORD), 소셜 전용 회원은 생략(첫 설정)
public record PasswordChangeRequest(
        String currentPassword,

        // 가입과 같은 규칙 (BCrypt 72바이트 상한)
        @NotBlank(message = "새 비밀번호를 입력해야 합니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
        String newPassword
) {
}
