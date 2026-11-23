package com.sparta.springdeliverymini.mail;

// 보낼 메일 1통 — 본문은 텍스트만 (링크 1개짜리 안내라 HTML 템플릿이 필요 없음)
public record EmailMessage(String to, String subject, String text) {

    // 로그에 남길 때 주소·본문(재설정 링크)을 그대로 쓰지 않도록 toString을 가림
    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}
