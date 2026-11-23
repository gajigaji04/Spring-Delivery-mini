package com.sparta.springdeliverymini.config;

import com.sparta.springdeliverymini.mail.BrevoEmailSender;
import com.sparta.springdeliverymini.mail.EmailSendException;
import com.sparta.springdeliverymini.mail.EmailSender;
import com.sparta.springdeliverymini.mail.LoggingEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

// 메일 발송 채널 (V23 비밀번호 재설정) — 발송 스레드는 mail.PasswordResetMailer가 직접 가짐
// (Executor 빈을 등록하면 Spring Boot 기본 applicationTaskExecutor가 만들어지지 않으므로 빈으로 두지 않음)
@Configuration
public class MailConfig {

    // 발송 채널 — 테스트의 가짜 발송기가 있으면 만들지 않음
    // none: 호출되면 실패하는 발송기 (PasswordResetService가 MailProperties.enabled()로 먼저 503을 응답하므로 호출되지 않음)
    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender emailSender(MailProperties properties, ObjectMapper objectMapper) {
        return switch (properties.sender()) {
            case NONE -> message -> {
                throw new EmailSendException("메일 발송이 꺼져 있습니다 (mail.sender=none)");
            };
            case LOG -> new LoggingEmailSender();
            case BREVO -> BrevoEmailSender.create(properties, objectMapper);
        };
    }
}
