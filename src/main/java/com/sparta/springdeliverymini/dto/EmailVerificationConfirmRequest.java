package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.NotBlank;

// 이메일 인증 (V24) POST /api/users/email-verification/confirm — 인증 메일 링크의 #token= 값
public record EmailVerificationConfirmRequest(
        @NotBlank(message = "인증 토큰이 필요합니다.")
        String token
) {
}
