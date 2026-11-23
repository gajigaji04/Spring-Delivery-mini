package com.sparta.springdeliverymini.mail;

import lombok.extern.slf4j.Slf4j;

// 로컬·테스트용 발송기 (mail.sender=log) — 메일을 보내지 않고 본문을 로그로 출력
// 본문에 재설정 링크(비밀 값)가 들어 있으므로 prod 프로필에서는 쓸 수 없음 (config.ProdProfileGuard)
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.info("[메일 발송(log)] to={}, subject={}\n{}", message.to(), message.subject(), message.text());
    }
}
