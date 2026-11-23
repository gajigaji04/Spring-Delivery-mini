package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 찾기 — 재설정 링크 요청 (V23) POST /api/users/password-reset
// 가입 여부와 관계없이 항상 202 (PasswordResetService)
public record PasswordResetRequest(
        @NotBlank(message = "이메일을 입력해야 합니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email
) {
}
