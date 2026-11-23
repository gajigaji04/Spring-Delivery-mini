package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 이메일 등록·변경 (V23) — PATCH /api/users/me/email
// currentPassword: 비밀번호가 있는 회원은 필수 — Access Token만 훔친 사람이 이메일을 바꾼 뒤
//                  비밀번호 찾기로 계정을 가져가지 못하도록. 소셜 전용 회원은 생략
public record EmailChangeRequest(
        @NotBlank(message = "이메일을 입력해야 합니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        String currentPassword
) {
}
