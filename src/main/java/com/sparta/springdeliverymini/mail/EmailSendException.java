package com.sparta.springdeliverymini.mail;

// 메일 발송 실패 — 메시지에 API 키·수신 주소를 넣지 않음 (로그에 남으므로)
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }
}
