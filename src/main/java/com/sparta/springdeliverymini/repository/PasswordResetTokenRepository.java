package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 재설정 — 행 잠금으로 조회 (uk_password_reset_tokens_token_hash)
    // 같은 링크로 동시에 재설정해도 뒤 요청은 앞 요청의 커밋(usedAt)을 읽고 거부됨 → 링크 1개로 비밀번호가 한 번만 바뀜
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // 재요청 간격 제한 — since 이후에 발급한 링크가 있는지 (idx_password_reset_tokens_user_created)
    boolean existsByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    // 아직 쓸 수 있는 링크를 모두 무효화 — 새 링크 발급·비밀번호 변경·재설정 때 (가장 최근 링크 하나만 유효하도록)
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.usedAt IS NULL AND t.expiresAt > :now
            """)
    int invalidateActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
