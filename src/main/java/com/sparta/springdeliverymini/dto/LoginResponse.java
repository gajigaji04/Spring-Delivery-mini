package com.sparta.springdeliverymini.dto;

// 로그인 응답
// accessToken: 발급된 JWT, tokenType: 헤더에 붙일 접두어("Bearer")
public record LoginResponse(String accessToken, String tokenType) {

    // tokenType을 항상 "Bearer"로 고정해서 생성
    public static LoginResponse bearer(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}
