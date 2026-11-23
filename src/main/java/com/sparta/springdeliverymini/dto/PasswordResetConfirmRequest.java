package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 재설정 (V23) POST /api/users/password-reset/confirm — 메일 링크의 #token= 값 + 새 비밀번호
public record PasswordResetConfirmRequest(
        @NotBlank(message = "재설정 토큰이 필요합니다.")
        String token,

        @NotBlank(message = "새 비밀번호를 입력해야 합니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
        String newPassword
) {
}
