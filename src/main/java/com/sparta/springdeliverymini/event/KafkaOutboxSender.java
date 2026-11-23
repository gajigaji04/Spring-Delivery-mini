package com.sparta.springdeliverymini.event;

import com.sparta.springdeliverymini.config.OutboxProperties;
import com.sparta.springdeliverymini.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

// Outbox → Kafka 발행 (#42) — 기본 전달 방법 (outbox.delivery=kafka 또는 지정 없음)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.delivery", havingValue = "kafka", matchIfMissing = true)
public class KafkaOutboxSender implements OutboxSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    // 메시지 키 = 주문 id → 같은 주문의 이벤트는 같은 파티션
    // 메타데이터를 못 받는 등 send 자체가 예외를 던져도 실패 결과로 바꿔 다른 이벤트 발행은 계속
    @Override
    public CompletableFuture<?> send(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topic(), String.valueOf(event.getAggregateId()), event.getPayload());
        record.headers()
                .add(OutboxPublisher.HEADER_EVENT_ID, event.getEventId().toString().getBytes(StandardCharsets.UTF_8))
                .add(OutboxPublisher.HEADER_EVENT_TYPE, event.getEventType().name().getBytes(StandardCharsets.UTF_8));
        try {
            return kafkaTemplate.send(record);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
