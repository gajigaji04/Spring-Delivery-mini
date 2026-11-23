package com.sparta.springdeliverymini.controller;

import com.sparta.springdeliverymini.dto.DemoAccountResponse;
import com.sparta.springdeliverymini.dto.LoginResponse;
import com.sparta.springdeliverymini.service.DemoLoginService;
import com.sparta.springdeliverymini.service.IssuedTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 배포 데모 체험 — 토큰 없이 호출 (SecurityConfig permitAll), 데모가 꺼져 있으면 빈 목록·404 (service.DemoLoginService)
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class DemoLoginController {

    private final DemoLoginService demoLoginService;
    private final RefreshTokenCookies refreshTokenCookies;

    @GetMapping("/demo-accounts")
    public List<DemoAccountResponse> accounts() {
        return demoLoginService.getAccounts();
    }

    // 응답은 일반 로그인과 같음: Access Token(본문) + Refresh Token(HttpOnly 쿠키)
    @PostMapping("/demo-login/admin")
    public ResponseEntity<LoginResponse> loginAsDemoAdmin() {
        IssuedTokens tokens = demoLoginService.loginAsDemoAdmin();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(tokens.refreshToken()).toString())
                .body(tokens.body());
    }
}
