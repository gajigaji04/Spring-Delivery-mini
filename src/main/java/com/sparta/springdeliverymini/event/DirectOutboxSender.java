package com.sparta.springdeliverymini.event;

import com.sparta.springdeliverymini.entity.OutboxEvent;
import com.sparta.springdeliverymini.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;

// Outbox → 알림 처리 직접 호출 (outbox.delivery=direct) — Kafka를 띄울 수 없는 무료 배포(prod-free 프로필)용
// Kafka 경로의 Consumer(NotificationEventListener)가 하던 일(본문 해석 → NotificationService.handle)을 같은 프로세스에서 바로 함
//
// - 별도 트랜잭션(REQUIRES_NEW): OutboxPublisher의 트랜잭션(행 잠금·발행 결과 기록)에 합류하면 알림 처리 예외가
//   바깥 트랜잭션을 rollback-only로 만들어 실패 기록까지 함께 롤백됨 → Kafka Consumer처럼 따로 커밋·롤백
// - 알림 저장이 커밋된 뒤 발행 결과(PUBLISHED) 기록이 실패하면 다음 주기에 다시 전달됨
//   → Kafka 경로와 같이 최소 한 번 전달, 중복은 NotificationService가 eventId(consumed_events)로 걸러냄
// - 처리 실패(형식 오류 포함)는 실패한 Future로 돌려줌 → OutboxPublisher가 백오프 재시도, 한도를 넘으면 FAILED
//   (Kafka 경로의 Consumer 재시도·DLT 대신. FAILED는 관리자 재발행 API로 다시 PENDING)
// - 사장님 실시간 알림(SSE)은 NotificationService가 이 트랜잭션 커밋 후에 보냄 (Kafka 경로와 같음)
@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.delivery", havingValue = "direct")
public class DirectOutboxSender implements OutboxSender {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final TransactionTemplate requiresNew;

    public DirectOutboxSender(ObjectMapper objectMapper, NotificationService notificationService,
                              PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CompletableFuture<?> send(OutboxEvent event) {
        try {
            DeliveryEventMessage message = NotificationEventListener.parse(objectMapper, event.getPayload());
            Boolean saved = requiresNew.execute(status -> notificationService.handle(message));
            log.info("이벤트 직접 전달 eventId={}, type={}, orderId={}, result={}",
                    message.eventId(), message.eventType(), message.orderId(),
                    Boolean.TRUE.equals(saved) ? "알림 저장" : "중복 무시");
            return CompletableFuture.completedFuture(saved);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
