package com.sparta.springdeliverymini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

// application.yml의 password-reset.* — 비밀번호 재설정 링크 (V23, service.PasswordResetService)
// url: 메일 링크의 프론트 화면 주소. 토큰은 쿼리가 아니라 #token= 조각으로 붙임
//      → 조각은 서버로 전송되지 않아 Vercel·Render 접근 로그, Referer 헤더에 토큰이 남지 않음
// ttl: 링크 유효 시간 / resend-cooldown: 같은 회원에게 링크를 다시 보내기까지의 최소 간격(메일 폭탄 방지)
@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(String url, Duration ttl, Duration resendCooldown) {

    public PasswordResetProperties {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("password-reset.url(PASSWORD_RESET_URL)이 필요합니다.");
        }
        url = url.trim();
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IllegalArgumentException("password-reset.url은 http(s) 주소여야 합니다.");
        }
        if (uri.getRawFragment() != null || url.endsWith("#")) {
            throw new IllegalArgumentException("password-reset.url에는 #을 넣지 않습니다 (토큰을 #token=으로 붙임).");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("password-reset.ttl은 0보다 커야 합니다.");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("password-reset.resend-cooldown은 0 이상이어야 합니다.");
        }
    }

    public String linkFor(String rawToken) {
        return url + "#token=" + rawToken;
    }
}
