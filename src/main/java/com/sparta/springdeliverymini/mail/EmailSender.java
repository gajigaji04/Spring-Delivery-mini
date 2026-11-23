package com.sparta.springdeliverymini.mail;

// 메일 발송 채널 — mail.sender: none(기본, prod) / log(로컬·테스트) / brevo (config.MailConfig에서 하나만 빈으로 등록)
// 실패는 EmailSendException — 호출하는 쪽(PasswordResetMailer)이 로그만 남기고 삼킴 (응답은 이미 나감)
public interface EmailSender {

    void send(EmailMessage message);
}
