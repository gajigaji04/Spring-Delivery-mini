package com.sparta.springdeliverymini.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// 이메일 인증 토큰 (V24) — 인증 메일 링크 1개 = 행 1개, 원문은 저장하지 않고 SHA-256 해시만 (service.EmailVerificationService)
// - 인증할 주소(email)를 함께 저장: 인증이 끝나야 users.email이 됨 (그 전에는 기존 주소가 그대로 비밀번호 찾기에 쓰임)
// - 한 번만 쓸 수 있음: 인증·새 링크 발급·변경 취소 중 하나가 일어나면 usedAt이 채워지고 다시 살아나지 않음
// - 주소·해시·만료 시각은 생성 후 바뀌지 않음 (PasswordResetToken과 같은 방식)
@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private EmailVerificationToken(Long userId, String email, String tokenHash,
                                   LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // email은 User.normalizeEmail로 정규화한 값 (DB CHECK email = lower(email))
    public static EmailVerificationToken issue(Long userId, String email, String tokenHash,
                                               LocalDateTime now, Duration ttl) {
        if (userId == null || email == null || email.isBlank() || tokenHash == null || tokenHash.isBlank()
                || now == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("이메일 인증 토큰 생성에 필요한 값이 올바르지 않습니다.");
        }
        return new EmailVerificationToken(userId, email, tokenHash, now, now.plus(ttl));
    }

    // 아직 쓰지 않았고 만료 전 (만료 시각과 같은 순간부터는 쓸 수 없음)
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    // 인증에 사용 — 쓸 수 없는 토큰이면 예외 (호출하는 쪽이 isUsable로 먼저 판단해 400으로 응답)
    public void use(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("이미 사용했거나 만료된 이메일 인증 토큰입니다.");
        }
        this.usedAt = now;
    }
}
