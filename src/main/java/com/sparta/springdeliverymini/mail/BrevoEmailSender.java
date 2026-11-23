package com.sparta.springdeliverymini.mail;

import com.sparta.springdeliverymini.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Brevo 트랜잭션 메일 API 발송기 (mail.sender=brevo, V23 비밀번호 재설정)
//
// SMTP가 아니라 HTTPS API를 쓰는 이유: 무료 배포(Render Free)는 SMTP 포트(25·465·587)로 나가는 연결을 막음
// Brevo는 도메인 없이 발신 주소 1개 인증만으로 보낼 수 있고 무료 한도(하루 300통)가 데모에 충분함
// - 실패(연결·응답 시간 초과, 비 2xx)는 EmailSendException — 재시도하지 않음 (사용자가 링크를 다시 요청하면 됨)
// - 예외 메시지·로그에 API 키·수신 주소를 넣지 않음
@Slf4j
public class BrevoEmailSender implements EmailSender {

    private static final int MAX_ERROR_BODY_LENGTH = 200;

    private final HttpClient httpClient;
    private final URI apiUri;
    private final String apiKey;
    private final String fromAddress;
    private final String fromName;
    private final Duration readTimeout;
    private final ObjectMapper objectMapper;

    BrevoEmailSender(HttpClient httpClient, URI apiUri, String apiKey, String fromAddress, String fromName,
                     Duration readTimeout, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.apiUri = apiUri;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.readTimeout = readTimeout;
        this.objectMapper = objectMapper;
    }

    public static BrevoEmailSender create(MailProperties properties, ObjectMapper objectMapper) {
        MailProperties.Brevo brevo = properties.brevo();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(brevo.connectTimeout())
                // API는 리다이렉트하지 않음 — 따라가면 API 키 헤더가 예상하지 못한 곳으로 갈 수 있음
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        log.info("메일 발송기: Brevo API (connectTimeout={}, readTimeout={})", brevo.connectTimeout(), brevo.readTimeout());
        return new BrevoEmailSender(httpClient, URI.create(brevo.apiUrl().trim()), brevo.apiKey().trim(),
                properties.fromAddress().trim(), properties.fromName(), brevo.readTimeout(), objectMapper);
    }

    @Override
    public void send(EmailMessage message) {
        HttpRequest request = HttpRequest.newBuilder(apiUri)
                .timeout(readTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body(message), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw new EmailSendException("Brevo 연결 시간 초과");
        } catch (HttpTimeoutException e) {
            throw new EmailSendException("Brevo 응답 시간 초과");
        } catch (IOException e) {
            throw new EmailSendException("Brevo 요청 실패 (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Brevo 요청 중 인터럽트");
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // 오류 본문은 {"code":"unauthorized","message":"..."} 형태 — 원인 파악용으로 앞부분만
            String body = response.body() == null ? "" : response.body();
            throw new EmailSendException("Brevo 응답 " + status + ": "
                    + body.substring(0, Math.min(body.length(), MAX_ERROR_BODY_LENGTH)));
        }
    }

    String body(EmailMessage message) {
        return objectMapper.writeValueAsString(Map.of(
                "sender", Map.of("email", fromAddress, "name", fromName),
                "to", List.of(Map.of("email", message.to())),
                "subject", message.subject(),
                "textContent", message.text()));
    }
}
