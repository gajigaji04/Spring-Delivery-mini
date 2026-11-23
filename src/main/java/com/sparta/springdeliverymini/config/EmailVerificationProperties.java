package com.sparta.springdeliverymini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

// application.yml의 email-verification.* — 이메일 인증 링크 (V24, service.EmailVerificationService)
// url: 메일 링크의 프론트 화면 주소. 토큰은 #token= 조각으로 붙임 (PasswordResetProperties와 같은 이유 — 서버 로그·Referer에 남지 않음)
// ttl: 링크 유효 시간 / resend-cooldown: 같은 회원에게 인증 메일을 다시 보내기까지의 최소 간격
//   (로그인한 회원이 아무 주소로나 메일을 반복해 보내 메일 폭탄·발송 한도 소진을 일으키지 않도록)
@ConfigurationProperties(prefix = "email-verification")
public record EmailVerificationProperties(String url, Duration ttl, Duration resendCooldown) {

    public EmailVerificationProperties {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("email-verification.url(EMAIL_VERIFICATION_URL)이 필요합니다.");
        }
        url = url.trim();
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IllegalArgumentException("email-verification.url은 http(s) 주소여야 합니다.");
        }
        if (uri.getRawFragment() != null || url.endsWith("#")) {
            throw new IllegalArgumentException("email-verification.url에는 #을 넣지 않습니다 (토큰을 #token=으로 붙임).");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("email-verification.ttl은 0보다 커야 합니다.");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("email-verification.resend-cooldown은 0 이상이어야 합니다.");
        }
    }

    public String linkFor(String rawToken) {
        return url + "#token=" + rawToken;
    }
}
