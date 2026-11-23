package com.sparta.springdeliverymini.controller;

import lombok.RequiredArgsConstructor;
import com.sparta.springdeliverymini.dto.EmailChangeRequest;
import com.sparta.springdeliverymini.dto.EmailVerificationConfirmRequest;
import com.sparta.springdeliverymini.dto.LoginRequest;
import com.sparta.springdeliverymini.dto.LoginResponse;
import com.sparta.springdeliverymini.dto.MyInfoResponse;
import com.sparta.springdeliverymini.dto.PasswordChangeRequest;
import com.sparta.springdeliverymini.dto.PasswordResetConfirmRequest;
import com.sparta.springdeliverymini.dto.PasswordResetRequest;
import com.sparta.springdeliverymini.dto.SignupRequest;
import com.sparta.springdeliverymini.dto.UserResponse;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.service.AuthTokenService;
import com.sparta.springdeliverymini.service.EmailVerificationService;
import com.sparta.springdeliverymini.service.IssuedTokens;
import com.sparta.springdeliverymini.service.PasswordResetService;
import com.sparta.springdeliverymini.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor // final 필드를 받는 생성자 자동 생성 (생성자 주입)
public class UserController {

    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    // 회원가입 — 토큰 없이 호출 가능 (SecurityConfig에서 permitAll)
    // @Valid: SignupRequest의 검증 조건(아이디 4~20자 등)을 어기면 → 400
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        // 가입 성공 시 201 Created와 회원 정보(비밀번호 제외) 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
    }

    // 로그인 — 토큰 없이 호출 가능 (SecurityConfig에서 permitAll)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 로그인 성공 시 200 OK와 Access Token(본문), Refresh Token(HttpOnly 쿠키)
        // 클라이언트는 이후 요청 헤더에 Authorization: Bearer {accessToken}을 붙여 보냄
        return withRefreshCookie(userService.login(request));
    }

    // Access Token 재발급 — Refresh Token 쿠키로만 인증 (SecurityConfig에서 permitAll)
    // 성공: 새 Access Token + 새 Refresh Token 쿠키 (기존 Refresh Token은 폐기됨, rotation)
    // 실패: 401 + code (REFRESH_TOKEN_*) — 더 쓸 수 없는 쿠키이므로 브라우저 쿠키도 삭제
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        IssuedTokens tokens;
        try {
            tokens = authTokenService.refresh(refreshToken);
        } catch (ApiException e) {
            // 오류 응답 본문은 GlobalExceptionHandler가 작성, 여기서 넣은 Set-Cookie 헤더는 그대로 나감
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookies.expire().toString());
            throw e;
        }
        return withRefreshCookie(tokens);
    }

    // 로그아웃 — 이 로그인(계열)의 Refresh Token 폐기 + 쿠키 삭제, 항상 204 (멱등)
    // Access Token 없이도 호출 가능: Access가 만료된 상태에서도 로그아웃할 수 있어야 함
    // 이미 발급된 Access Token은 만료까지 유효 → 클라이언트가 메모리에서 지움 (docs/api.md 인증 장)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        authTokenService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.expire().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(tokens.refreshToken()).toString())
                .body(tokens.body());
    }

    // 내 정보 조회 (#62) — 로그인 사용자 누구나, 응답은 토큰 값이 아니라 DB 기준
    // (이전에는 토큰의 username·authorities를 Map으로 그대로 돌려줌 → 역할 변경이 반영되지 않았음)
    @GetMapping("/me")
    public MyInfoResponse me(Authentication authentication) {
        return userService.getMyInfo(authentication.getName());
    }

    // 비밀번호 변경·설정 (V23) — 로그인 사용자 본인, 성공 204
    // Refresh Token 쿠키(Path=/api/users라 이 요청에도 실림)로 지금 기기의 로그인을 찾아 그것만 남기고 나머지 로그인은 폐기
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        userService.changePassword(authentication.getName(), request, refreshToken);
        return ResponseEntity.noContent().build();
    }

    // 이메일 등록·변경 (V23) — 내 정보 반환
    // V24: 바로 바뀌지 않고 새 주소로 인증 메일 → 응답의 pendingEmail이 인증 대기 주소 (링크를 열면 email로 확정)
    @PatchMapping("/me/email")
    public MyInfoResponse changeEmail(Authentication authentication, @Valid @RequestBody EmailChangeRequest request) {
        return userService.changeEmail(authentication.getName(), request);
    }

    // 이메일 인증 (V24, 토큰 없이 호출 — 메일을 다른 기기에서 열 수 있음) — 성공 204
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Void> confirmEmailVerification(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    // 비밀번호 찾기 — 재설정 링크 메일 요청 (V23, 토큰 없이 호출). 가입 여부와 관계없이 202
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    // 비밀번호 재설정 (V23, 토큰 없이 호출) — 성공 204, 이 회원의 모든 로그인이 폐기되므로 다시 로그인
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
