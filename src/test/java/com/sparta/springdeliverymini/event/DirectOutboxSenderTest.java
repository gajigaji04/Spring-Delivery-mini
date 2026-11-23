package com.sparta.springdeliverymini.event;

import com.sparta.springdeliverymini.entity.OutboxEvent;
import com.sparta.springdeliverymini.entity.OutboxEventType;
import com.sparta.springdeliverymini.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DirectOutboxSender — Kafka 없이 알림 처리를 직접 호출 (outbox.delivery=direct, prod-free)
// 실제 DB 트랜잭션·중복 제거·발행 결과 기록은 ProdFreeProfileIntegrationTest
@ExtendWith(MockitoExtension.class)
class DirectOutboxSenderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);

    @Mock private NotificationService notificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private DirectOutboxSender sender;

    @BeforeEach
    void setUp() {
        sender = new DirectOutboxSender(objectMapper, notificationService, transactionManager);
    }

    private void givenTransaction() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }

    private OutboxEvent event(DeliveryEventMessage message) {
        return OutboxEvent.pending(message.eventId(), message.eventType(), message.orderId(),
                objectMapper.writeValueAsString(message), NOW);
    }

    private static DeliveryEventMessage message() {
        return new DeliveryEventMessage(UUID.randomUUID(), OutboxEventType.PAYMENT_COMPLETED,
                1L, 2L, 3L, 4L, 5L, 10000, NOW);
    }

    @Test
    void 본문을_해석해_새_트랜잭션에서_알림_처리를_호출하고_성공_결과를_돌려준다() {
        givenTransaction();
        DeliveryEventMessage message = message();
        given(notificationService.handle(message)).willReturn(true);

        CompletableFuture<?> result = sender.send(event(message));

        assertThat(result.join()).isEqualTo(true);
        // 발행 트랜잭션에 합류하지 않고 따로 커밋·롤백 (알림 처리 실패가 발행 실패 기록까지 롤백하지 않도록)
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(any());
    }

    @Test
    void 이미_처리한_이벤트면_중복으로_보고_성공으로_돌려준다() {
        givenTransaction();
        DeliveryEventMessage message = message();
        given(notificationService.handle(message)).willReturn(false);

        assertThat(sender.send(event(message)).join()).isEqualTo(false);
    }

    @Test
    void 알림_처리가_실패하면_롤백하고_예외를_던지지_않고_실패한_결과를_돌려준다() {
        givenTransaction();
        DeliveryEventMessage message = message();
        given(notificationService.handle(message)).willThrow(new IllegalStateException("DB 장애"));

        CompletableFuture<?> result = sender.send(event(message));

        assertThat(result).isCompletedExceptionally();
        verify(transactionManager).rollback(any());
    }

    @Test
    void 본문_형식이_잘못되면_알림_처리를_호출하지_않고_실패한_결과를_돌려준다() {
        OutboxEvent broken = OutboxEvent.pending(UUID.randomUUID(), OutboxEventType.PAYMENT_COMPLETED, 1L, "{not-json", NOW);

        CompletableFuture<?> result = sender.send(broken);

        assertThat(result).isCompletedExceptionally();
        verify(notificationService, never()).handle(any());
    }
}
