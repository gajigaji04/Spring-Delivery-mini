package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디를 입력해야 합니다.")
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
        String username,

        // BCrypt는 72바이트까지만 처리하므로 상한을 둠
        @NotBlank(message = "비밀번호를 입력해야 합니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotNull(message = "role은 CUSTOMER 또는 OWNER를 입력해야 합니다.")
        Role role
) {
}
