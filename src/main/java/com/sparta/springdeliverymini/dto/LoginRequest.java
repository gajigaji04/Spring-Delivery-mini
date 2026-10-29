package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인 요청 — 아이디/비밀번호가 비어 있으면 400
public record LoginRequest(
        @NotBlank(message = "아이디를 입력해야 합니다.")
        String username,

        @NotBlank(message = "비밀번호를 입력해야 합니다.")
        String password
) {
}
