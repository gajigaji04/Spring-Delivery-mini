package com.sparta.springdeliverymini.event;

import com.sparta.springdeliverymini.entity.OutboxEvent;

import java.util.concurrent.CompletableFuture;

// Outbox 이벤트 1건을 알림 처리 쪽으로 전달하는 방법 (outbox.delivery, docs/architecture.md Kafka 장)
// - kafka(기본): Kafka 토픽으로 발행 → NotificationEventListener가 소비 (KafkaOutboxSender)
// - direct: Kafka 없이 같은 프로세스에서 알림 처리를 바로 호출 (DirectOutboxSender, 무료 배포 prod-free 프로필)
// OutboxPublisher의 행 잠금·재시도·백오프·FAILED 처리는 전달 방법과 관계없이 같게 두려고 전달만 분리
// 반환값: 전달 결과. 바로 실패하면 이미 실패한 Future (예외를 던지지 않음 → batch의 다른 이벤트 처리는 계속)
public interface OutboxSender {

    CompletableFuture<?> send(OutboxEvent event);
}
