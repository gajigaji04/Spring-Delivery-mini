package com.sparta.springdeliverymini.controller;

import com.sparta.springdeliverymini.service.DemoDataResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

// 배포 데모 데이터 초기화 — ADMIN (SecurityConfig /api/admin/**), 데모가 꺼진 배포는 404 (service.DemoDataResetService)
@RestController
@RequestMapping("/api/admin/demo-data")
@RequiredArgsConstructor
public class AdminDemoDataController {

    private final DemoDataResetService demoDataResetService;

    @PostMapping("/reset")
    public Map<String, LocalDateTime> reset(Authentication authentication) {
        return Map.of("resetAt", demoDataResetService.reset(authentication.getName()));
    }
}
