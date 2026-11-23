package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // 이메일(V23)은 User.normalizeEmail로 정규화한 값으로만 조회 — 저장 값도 같은 규칙이라 대소문자 무시 비교가 필요 없음
    boolean existsByEmail(String email);

    // 비밀번호 재설정 요청 — 같은 회원의 요청이 동시에 와도 재요청 간격 검사·링크 발급이 하나씩 진행되도록 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    // 소셜 계정 해제 (V22) — 같은 회원의 해제 요청이 동시에 와도 "마지막 로그인 수단" 검사가 하나씩 진행되도록
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
}
