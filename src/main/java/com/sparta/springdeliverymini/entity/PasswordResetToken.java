package com.sparta.springdeliverymini.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// 비밀번호 재설정 토큰 (V23) — 메일 링크 1개 = 행 1개, 원문은 저장하지 않고 SHA-256 해시만 (service.PasswordResetService)
// - 한 번만 쓸 수 있음: 사용·새 링크 발급·비밀번호 변경 중 하나가 일어나면 usedAt이 채워지고 다시 살아나지 않음
// - 해시·만료 시각은 생성 후 바뀌지 않음
// - 회원은 id로만 참조(DB에는 FK) — RefreshToken과 같은 방식
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private PasswordResetToken(Long userId, String tokenHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static PasswordResetToken issue(Long userId, String tokenHash, LocalDateTime now, Duration ttl) {
        if (userId == null || tokenHash == null || tokenHash.isBlank() || now == null
                || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰 생성에 필요한 값이 올바르지 않습니다.");
        }
        return new PasswordResetToken(userId, tokenHash, now, now.plus(ttl));
    }

    // 아직 쓰지 않았고 만료 전 (만료 시각과 같은 순간부터는 쓸 수 없음)
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    // 재설정에 사용 — 쓸 수 없는 토큰이면 예외 (호출하는 쪽이 isUsable로 먼저 판단해 400으로 응답)
    public void use(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("이미 사용했거나 만료된 비밀번호 재설정 토큰입니다.");
        }
        this.usedAt = now;
    }
}
