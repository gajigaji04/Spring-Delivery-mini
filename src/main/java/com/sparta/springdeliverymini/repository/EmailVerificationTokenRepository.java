package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    // 인증 — 행 잠금으로 조회 (uk_email_verification_tokens_token_hash)
    // 같은 링크를 동시에 열어도 뒤 요청은 앞 요청의 커밋(usedAt)을 읽고 거부됨 → 링크 1개는 한 번만 쓰임
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // 재요청 간격 제한 — since 이후에 발급한 링크가 있는지 (idx_email_verification_tokens_user_created)
    boolean existsByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    // 인증 대기 중인 주소 — 새 링크를 발급하면 이전 링크는 무효라 쓸 수 있는 행은 많아야 1개
    Optional<EmailVerificationToken> findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime now);

    // 아직 쓸 수 있는 링크를 모두 무효화 — 새 링크 발급·인증 완료·변경 취소 때 (가장 최근 링크 하나만 유효하도록)
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE EmailVerificationToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.usedAt IS NULL AND t.expiresAt > :now
            """)
    int invalidateActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
