package com.sparta.springdeliverymini.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

// application.yml의 mail.* — 메일 발송 설정 (V23 비밀번호 재설정, config.MailConfig)
// sender: none(발송 안 함 → 비밀번호 찾기 503) / log(본문을 로그로, 로컬·테스트) / brevo(Brevo API)
//   알 수 없는 값은 enum 바인딩에서 실패 → 오타로 조용히 다른 발송기가 쓰이지 않도록
// from-address: 발신 주소 (brevo는 Brevo 콘솔에서 인증한 주소여야 함) / from-name: 받는 사람에게 보이는 이름
@ConfigurationProperties(prefix = "mail")
public record MailProperties(Sender sender, String fromAddress, String fromName, Brevo brevo) {

    public enum Sender { NONE, LOG, BREVO }

    public MailProperties {
        if (sender == null) {
            sender = Sender.NONE;
        }
        if (!StringUtils.hasText(fromName)) {
            fromName = "Spring Delivery";
        }
        // brevo를 골랐는데 키·발신 주소가 없으면 모든 발송이 실패함 → 기동 시점에 막음
        // (prod는 ProdProfileGuard가 빈 생성 전에 환경변수 이름으로 한 번 더 알려 줌). 값은 메시지에 넣지 않음
        if (sender == Sender.BREVO) {
            if (brevo == null || !StringUtils.hasText(brevo.apiKey()) || !StringUtils.hasText(brevo.apiUrl())) {
                throw new IllegalArgumentException("mail.sender=brevo이면 mail.brevo.api-key(BREVO_API_KEY)가 필요합니다.");
            }
            if (!StringUtils.hasText(fromAddress)) {
                throw new IllegalArgumentException("mail.sender=brevo이면 mail.from-address(MAIL_FROM_ADDRESS)가 필요합니다.");
            }
            brevo.validate();
        }
    }

    // 메일을 실제로 보낼 수 있는지 — none이면 비밀번호 찾기 요청을 503으로 거절 (링크를 만들어도 전달할 방법이 없음)
    public boolean enabled() {
        return sender != Sender.NONE;
    }

    // api-key는 기본값 없이 BREVO_API_KEY로만 — toString에 값을 넣지 않음
    // connect-timeout·read-timeout: 발송은 요청 스레드가 아니라 메일 전용 스레드에서 하지만, 상한이 없으면 큐가 막힘
    public record Brevo(String apiKey, String apiUrl, Duration connectTimeout, Duration readTimeout) {

        void validate() {
            if (connectTimeout == null || readTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                    || readTimeout.isNegative() || readTimeout.isZero()) {
                throw new IllegalArgumentException("mail.brevo.connect-timeout, read-timeout은 0보다 커야 합니다.");
            }
        }

        @Override
        public String toString() {
            return "Brevo[apiUrl=" + apiUrl + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
        }
    }
}
