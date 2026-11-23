package com.sparta.springdeliverymini.entity;

import com.sparta.springdeliverymini.exception.InvalidOwnerApplicationStatusException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

// BaseEntity 상속 → created_at(가입 시각), updated_at(정보 수정 시각) 자동 기록
@Entity
@Table(name = "users") // user는 PostgreSQL 예약어라 테이블명을 users로 지정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 new 금지
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String username;

    // BCrypt 해시. 소셜 전용 회원은 null (V22) — 아이디·비밀번호 로그인 불가
    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 비밀번호 재설정 링크를 받는 주소 (V23). 없으면 null — 비밀번호 찾기를 쓸 수 없음
    // 항상 정규화(앞뒤 공백 제거·소문자)해서 저장: DB CHECK(email = lower(email)) + UNIQUE로 한 주소 = 한 회원
    @Column(unique = true)
    private String email;

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // 소셜 로그인 첫 가입 (V22) — 가입 경로와 같이 항상 CUSTOMER, 비밀번호 없음
    public static User socialCustomer(String username) {
        return new User(username, null, Role.CUSTOMER);
    }

    // 이메일 등록·변경 (V23) — 형식 검사는 요청 DTO(@Email), 중복은 DB UNIQUE가 최종 근거
    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    // 비밀번호 변경·재설정·소셜 전용 회원의 첫 설정 (V23) — 인코딩된 해시만 받음 (평문은 엔티티에 들어오지 않음)
    public void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 필요합니다.");
        }
        this.password = encodedPassword;
    }

    // 조회·저장에 같은 규칙을 쓰도록 공개 (재설정 요청의 이메일 조회도 이 값으로)
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean hasPassword() {
        return password != null;
    }

    // 사장님 전환 — 관리자 승인(OwnerApplication.approve)에서만 호출 (V17 승인제)
    // 이미 발급된 Access Token의 role은 그대로라 /api/users/refresh 이후부터 OWNER로 인가됨
    // (그 사이에도 Service가 DB 역할을 다시 확인하므로 고객 권한으로 남는 일 없음)
    public void promoteToOwner() {
        if (role != Role.CUSTOMER) {
            throw new InvalidOwnerApplicationStatusException("고객 계정만 사장님으로 전환할 수 있습니다. 현재 역할: " + role);
        }
        this.role = Role.OWNER;
    }
}