package com.sparta.springdeliverymini.controller;

import lombok.RequiredArgsConstructor;
import com.sparta.springdeliverymini.dto.LoginRequest;
import com.sparta.springdeliverymini.dto.LoginResponse;
import com.sparta.springdeliverymini.dto.SignupRequest;
import com.sparta.springdeliverymini.dto.UserResponse;
import com.sparta.springdeliverymini.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor // final 필드를 받는 생성자 자동 생성 (생성자 주입)
public class UserController {

    private final UserService userService;

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
        // 로그인 성공 시 200 OK와 JWT 반환
        // 클라이언트는 이후 요청 헤더에 Authorization: Bearer {accessToken}을 붙여 보냄
        return ResponseEntity.ok(userService.login(request));
    }

    // 로그인이 필요한 요청 예시 — Authorization: Bearer {토큰} 헤더 필요
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream().map(Object::toString).toList()
        ));
    }
}
