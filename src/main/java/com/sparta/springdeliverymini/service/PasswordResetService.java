package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.config.MailProperties;
import com.sparta.springdeliverymini.config.PasswordResetProperties;
import com.sparta.springdeliverymini.entity.PasswordResetToken;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.exception.ErrorCode;
import com.sparta.springdeliverymini.mail.EmailMessage;
import com.sparta.springdeliverymini.mail.PasswordResetMailer;
import com.sparta.springdeliverymini.repository.PasswordResetTokenRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

// 비밀번호 찾기(이메일 재설정 링크) — docs/architecture.md 인증 장 "비밀번호 재설정", docs/api.md 비밀번호 장
//
// 1. 요청: 이메일 → 가입되고 인증된 주소(V24, EmailVerificationService)면 1회용 링크(무작위 256비트 토큰, 30분)를 메일로. 응답은 가입 여부와 관계없이 항상 202
//    - 응답 본문·시간으로 "이 이메일이 가입돼 있다"를 알 수 없게 함 (발송은 커밋 뒤 전용 스레드, PasswordResetMailer)
//    - 같은 회원에게는 resend-cooldown(1분) 안에 다시 보내지 않음 (메일 폭탄·발송 한도 소진 방지) — 이때도 같은 202
//    - 새 링크를 발급하면 이전 링크는 무효 → 가장 최근 메일의 링크만 쓸 수 있음
// 2. 재설정: 토큰 + 새 비밀번호 → 비밀번호 변경, 토큰 사용 처리, 이 회원의 모든 로그인(Refresh Token) 폐기
//    - 토큰은 DB에 SHA-256 해시만 저장 (Refresh Token과 같은 이유 — AuthTokenService.hash)
//    - 행 잠금으로 같은 링크의 동시 재설정은 하나만 성공
//
// Redis를 쓰지 않음: 토큰은 비밀번호를 바꾸는 권한이라 유실·만료 정책을 DB 제약·행으로 확인할 수 있어야 함 ("Redis는 보조 수단")
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    // Base64 URL-safe, 패딩 없음 → 32바이트는 43자 (Refresh Token과 같은 형식)
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final MailProperties mailProperties;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestReset(String rawEmail) {
        // 메일을 보낼 방법이 없으면 링크를 만들지 않고 503 — 가입 여부와 관계없이 같은 응답이라 정보가 새지 않음
        if (!mailProperties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PASSWORD_RESET_UNAVAILABLE,
                    "지금은 비밀번호 찾기를 사용할 수 없습니다.");
        }
        String email = User.normalizeEmail(rawEmail);
        Optional<User> found = email == null ? Optional.empty() : userRepository.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        // 인증되지 않은 주소(V24 이전에 확인 없이 등록)로는 보내지 않음 — 응답은 없는 주소와 같은 202 (V24)
        // 그 주소가 회원 본인 것인지 확인된 적이 없어 계정 복구 수단으로 믿을 수 없음
        if (!user.isEmailVerified()) {
            log.info("인증되지 않은 이메일 — 비밀번호 재설정 메일 건너뜀 userId={}", user.getId());
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);

        // 재요청 간격 — 회원 행 잠금 안에서 확인하므로 동시 요청이 함께 통과하지 않음
        if (passwordResetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(),
                now.minus(properties.resendCooldown()))) {
            log.info("비밀번호 재설정 재요청 간격 안 — 발송 건너뜀 userId={}", user.getId());
            return;
        }

        passwordResetTokenRepository.invalidateActive(user.getId(), now);
        String raw = newRawToken();
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(user.getId(), AuthTokenService.hash(raw), now, properties.ttl()));

        mailer.sendAfterCommit(user.getId(), new EmailMessage(user.getEmail(), "[Spring Delivery] 비밀번호 재설정 안내",
                """
                %s 님, 비밀번호 재설정을 요청하셨습니다.

                아래 링크에서 새 비밀번호를 설정해 주세요. 링크는 %d분 동안 한 번만 쓸 수 있습니다.
                %s

                요청하지 않으셨다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(user.getUsername(), properties.ttl().toMinutes(), properties.linkFor(raw))));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 형식이 다르면 DB 조회 없이 거부 — 없는 토큰·만료·사용됨과 같은 응답 (어느 경우인지 알려주지 않음)
        PasswordResetToken token = Optional.ofNullable(rawToken)
                .filter(raw -> TOKEN_FORMAT.matcher(raw).matches())
                .flatMap(raw -> passwordResetTokenRepository.findByTokenHashForUpdate(AuthTokenService.hash(raw)))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findById(token.getUserId()).orElseThrow(PasswordResetService::invalidToken);

        token.use(now);
        user.changePassword(passwordEncoder.encode(newPassword));
        // 다른 링크(메일함에 남은 이전 링크 포함)도 무효, 모든 기기의 로그인을 끊음 — 비밀번호가 새었을 수 있는 상황이므로
        passwordResetTokenRepository.invalidateActive(user.getId(), now);
        int revoked = authTokenService.revokeOtherSessions(user.getId(), null);
        log.info("비밀번호 재설정 완료 userId={}, 폐기한 로그인 토큰={}", user.getId(), revoked);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PASSWORD_RESET_TOKEN_INVALID,
                "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 요청해주세요.");
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
