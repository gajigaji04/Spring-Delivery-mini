package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.EmailVerificationProperties;
import com.sparta.springdeliverymini.config.MailProperties;
import com.sparta.springdeliverymini.entity.EmailVerificationToken;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.mail.EmailMessage;
import com.sparta.springdeliverymini.mail.PasswordResetMailer;
import com.sparta.springdeliverymini.repository.EmailVerificationTokenRepository;
import com.sparta.springdeliverymini.repository.PasswordResetTokenRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

// 이메일 소유권 인증 (V24) — docs/architecture.md 인증 장 "이메일 인증", docs/api.md 비밀번호·이메일 장
//
// 왜: 확인 없이 등록한 주소로 비밀번호 재설정 링크를 보내면, 오타·남의 주소가 계정 복구 수단이 됨
//     → 링크를 연 사람(= 메일함 주인)만 그 주소를 users.email로 확정. 비밀번호 찾기는 인증된 주소만 (PasswordResetService)
//
// 1. 요청(가입 시 이메일 입력·내 정보에서 등록/변경): 주소 + 1회용 링크(무작위 256비트, 24시간)를 행으로 저장하고 그 주소로 메일
//    - users.email은 아직 바꾸지 않음 → 변경 중에도 기존(인증된) 주소로 비밀번호 찾기 가능
//    - 대기 중인 주소는 UNIQUE를 차지하지 않음 → 남의 주소를 먼저 적어 선점할 수 없음
//    - 새 링크를 발급하면 이전 링크는 무효 (가장 최근에 요청한 주소 하나만 대기)
//    - 내 정보에서의 요청은 resend-cooldown(1분) 안에 다시 받지 않음 (429) — 아무 주소로나 메일을 반복해 보내지 못하도록
// 2. 인증(링크 열기): 토큰 확인 → users.email·email_verified_at 확정, 토큰 사용 처리
//    - 그 사이 다른 회원이 같은 주소를 인증했으면 409 (DB UNIQUE가 최종 근거)
//    - 주소가 바뀌면 이전 주소로 보낸 비밀번호 재설정 링크도 무효
//    - 로그인 없이 호출: 메일을 다른 기기·브라우저에서 열 수 있음. 토큰 자체가 권한 (비밀번호 재설정과 같음)
//
// 토큰은 비밀번호 재설정 토큰과 같은 형식·저장 방식 (SHA-256 해시만, AuthTokenService.hash). Redis를 쓰지 않음 — 같은 이유
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String PURPOSE = "이메일 인증";
    private static final int TOKEN_BYTES = 32;
    // Base64 URL-safe, 패딩 없음 → 32바이트는 43자 (비밀번호 재설정 토큰과 같은 형식)
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailer mailer;
    private final MailProperties mailProperties;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    // 가입 직후 — 가입은 메일 발송 여부와 관계없이 성공해야 하므로 발송할 방법이 없으면(mail.sender=none) 건너뜀
    // (그 경우 주소는 저장되지 않음 → 내 정보에서 등록하면 503으로 이유가 드러남). 새 회원이라 재요청 간격 검사 없음
    // 호출하는 쪽(UserService.signup) 트랜잭션 안에서 실행 — 가입이 롤백되면 토큰·메일도 없음
    public void requestForNewUser(User user, String email) {
        if (!mailProperties.enabled()) {
            log.info("메일 발송이 꺼져 있어 가입 이메일 인증 메일을 보내지 않음 userId={}", user.getId());
            return;
        }
        issue(user, email, LocalDateTime.now(clock));
    }

    // 내 정보에서 등록·변경 — 호출하는 쪽(UserService.changeEmail)이 회원 행을 잠근 상태 → 재요청 간격 검사가 동시에 통과하지 않음
    public void request(User user, String email) {
        if (!mailProperties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE,
                    "지금은 이메일을 등록·변경할 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (tokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(properties.resendCooldown()))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT,
                    "인증 메일을 방금 보냈습니다. 잠시 후 다시 요청해주세요.");
        }
        issue(user, email, now);
    }

    // 진행 중인 변경 취소 — 인증된 지금 주소를 다시 입력한 경우 (대기 중인 링크를 모두 무효화)
    public void cancelPending(Long userId) {
        tokenRepository.invalidateActive(userId, LocalDateTime.now(clock));
    }

    // 인증 대기 중인 주소 (내 정보 화면 표시용), 없으면 null
    public String pendingEmail(Long userId) {
        return tokenRepository
                .findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, LocalDateTime.now(clock))
                .map(EmailVerificationToken::getEmail)
                .orElse(null);
    }

    @Transactional
    public void confirm(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 형식이 다르면 DB 조회 없이 거부 — 없는 토큰·만료·사용됨과 같은 응답 (어느 경우인지 알려주지 않음)
        EmailVerificationToken token = Optional.ofNullable(rawToken)
                .filter(raw -> TOKEN_FORMAT.matcher(raw).matches())
                .flatMap(raw -> tokenRepository.findByTokenHashForUpdate(AuthTokenService.hash(raw)))
                .filter(t -> t.isUsable(now))
                .orElseThrow(EmailVerificationService::invalidToken);
        User user = userRepository.findById(token.getUserId()).orElseThrow(EmailVerificationService::invalidToken);

        String email = token.getEmail();
        boolean changed = !email.equals(user.getEmail());
        // 대기하는 동안 다른 회원이 같은 주소를 먼저 인증함 — 토큰은 쓰지 않은 채로 둠(롤백)
        if (changed && userRepository.existsByEmail(email)) {
            throw emailAlreadyUsed();
        }
        token.use(now);
        user.verifyEmail(email, now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 주소를 인증 — DB UNIQUE가 최종 근거
            throw emailAlreadyUsed();
        }
        // flush 뒤에 실행 — 이 UPDATE의 자동 flush에서 제약 위반이 나지 않도록
        tokenRepository.invalidateActive(user.getId(), now);
        if (changed) {
            // 이전 주소로 보낸 비밀번호 재설정 링크는 더 쓸 수 없게 함 (주소를 바꾼 이유가 이전 메일함 유출일 수 있음)
            passwordResetTokenRepository.invalidateActive(user.getId(), now);
        }
        log.info("이메일 인증 완료 userId={}, 주소 변경={}", user.getId(), changed);
    }

    private void issue(User user, String email, LocalDateTime now) {
        tokenRepository.invalidateActive(user.getId(), now);
        String raw = newRawToken();
        tokenRepository.save(EmailVerificationToken.issue(user.getId(), email, AuthTokenService.hash(raw), now,
                properties.ttl()));

        mailer.sendAfterCommit(PURPOSE, user.getId(), new EmailMessage(email, "[Spring Delivery] 이메일 주소 인증",
                """
                %s 님, 이 주소를 Spring Delivery 계정 이메일로 등록하려면 아래 링크를 열어 인증해 주세요.
                인증을 마쳐야 이 주소로 비밀번호를 찾을 수 있습니다. 링크는 %s 동안 한 번만 쓸 수 있습니다.
                %s

                요청하지 않으셨다면 이 메일을 무시하세요. 이 주소는 등록되지 않습니다.
                """.formatted(user.getUsername(), describe(properties.ttl()), properties.linkFor(raw))));
    }

    // 메일 본문용 유효 시간 표기 — 시간 단위로 나누어떨어지면 "24시간", 아니면 "30분"
    static String describe(Duration ttl) {
        return ttl.toMinutes() % 60 == 0 ? ttl.toHours() + "시간" : ttl.toMinutes() + "분";
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                "인증 링크가 만료되었거나 이미 사용되었습니다. 내 정보에서 인증 메일을 다시 요청해주세요.");
    }

    private static ApiException emailAlreadyUsed() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_USED, "이미 사용 중인 이메일입니다.");
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
