package com.sparta.springdeliverymini.mail;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 재설정 메일 발송 예약 (V23) — 트랜잭션 커밋 뒤 메일 전용 스레드에서 보냄
//
// - 커밋 뒤: 토큰 행이 롤백됐는데 링크만 나가 "쓸 수 없는 링크"를 받는 일이 없도록
// - 전용 스레드: 요청 스레드가 외부 API를 기다리지 않음 → 응답 시간이 "가입된 이메일인지"에 따라 달라지지 않음
//   (가입 여부와 관계없이 바로 202). 큐가 차면 버림(로그만) — 호출 스레드에서 대신 보내면 응답 시간 차이가 다시 생김
// - 실패는 로그만 남기고 재시도하지 않음: 사용자가 링크를 다시 요청하면 됨 (재요청 간격 제한은 PasswordResetService)
// - Executor를 빈으로 두지 않음 — 등록하면 Spring Boot 기본 applicationTaskExecutor가 만들어지지 않음
@Slf4j
@Component
public class PasswordResetMailer {

    private static final int QUEUE_CAPACITY = 100;

    private final EmailSender emailSender;
    private final ThreadPoolExecutor executor;

    public PasswordResetMailer(EmailSender emailSender) {
        this.emailSender = emailSender;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "password-reset-mailer");
                    t.setDaemon(true);
                    return t;
                },
                (r, pool) -> log.warn("메일 발송 대기열이 가득 차 비밀번호 재설정 메일을 보내지 못함"));
    }

    // 트랜잭션 안이면 커밋 뒤에, 밖이면 바로 발송을 예약
    public void sendAfterCommit(Long userId, EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(userId, message);
                }
            });
        } else {
            submit(userId, message);
        }
    }

    private void submit(Long userId, EmailMessage message) {
        executor.execute(() -> {
            try {
                emailSender.send(message);
                log.info("비밀번호 재설정 메일 발송 userId={}", userId);
            } catch (RuntimeException e) {
                // 메시지에는 API 키·주소가 없음 (EmailSendException 규칙). 그 밖의 예외도 종류·메시지만
                log.warn("비밀번호 재설정 메일 발송 실패 userId={}: {}", userId, e.getMessage());
            }
        });
    }

    // 종료 시 남은 발송을 잠깐 기다림 (이미 응답한 요청의 메일이 사라지지 않도록)
    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
