package com.sparta.springdeliverymini.config;

import com.sparta.springdeliverymini.TestcontainersConfiguration;
import com.sparta.springdeliverymini.dto.*;
import com.sparta.springdeliverymini.entity.*;
import com.sparta.springdeliverymini.event.DirectOutboxSender;
import com.sparta.springdeliverymini.event.OutboxPublisher;
import com.sparta.springdeliverymini.event.OutboxPublisher.PublishResult;
import com.sparta.springdeliverymini.event.OutboxSender;
import com.sparta.springdeliverymini.repository.*;
import com.sparta.springdeliverymini.service.MenuService;
import com.sparta.springdeliverymini.service.OrderService;
import com.sparta.springdeliverymini.service.PaymentService;
import com.sparta.springdeliverymini.service.StoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 무료 배포 프로필(prod,prod-free)을 실제로 적용해 띄운 뒤 확인 — Kafka 없이 기동·이벤트 직접 전달·Actuator 노출
// KAFKA_BOOTSTRAP_SERVERS를 지정하지 않음 → ProdProfileGuard가 direct 모드에서 막지 않는지도 함께 확인
// 포트: 실제 배포처럼 PORT 환경변수 하나로 서비스·관리 포트를 정함 (RANDOM_PORT는 서비스 포트만 0으로 바꿔 관리 포트와 달라짐)
//   → 빈 포트를 골라 PORT로 넘기고 DEFINED_PORT로 그 값 그대로 띄움
// 발행은 스케줄러(1초 주기)와 겹치지 않도록 테스트에서 끄고 publishPending()을 직접 호출
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "DB_URL=jdbc:postgresql://overridden-by-testcontainers/delivery",
        "DB_USERNAME=guard-check-only",
        "DB_PASSWORD=guard-check-only",
        "JWT_SECRET=guard-check-only",
        "REDIS_HOST=localhost",
        "CORS_ALLOWED_ORIGINS=https://app.example.com",
        "outbox.publisher.enabled=false"
})
@ActiveProfiles({"test", "prod", "prod-free"})
@Import(TestcontainersConfiguration.class)
class ProdFreeProfileIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int serverPort;

    @DynamicPropertySource
    static void platformPort(DynamicPropertyRegistry registry) {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            registry.add("PORT", () -> port);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired private List<OutboxSender> senders;
    @Autowired private List<ScheduledTaskHolder> scheduledTaskHolders;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private StoreService storeService;
    @Autowired private MenuService menuService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;

    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentRefundRepository paymentRefundRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User customer;
    private User owner;
    private MenuResponse menu;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner1", "encoded-password", Role.OWNER));
        customer = userRepository.save(new User("customer1", "encoded-password", Role.CUSTOMER));
        StoreResponse store = storeService.createStore(new StoreCreateRequest("치킨집"), "owner1");
        menu = menuService.createMenu(new MenuCreateRequest(store.id(), "치킨", 10000L, null), "owner1");
    }

    @AfterEach
    void tearDown() {
        // 이벤트·알림 테이블은 FK가 없어 순서 무관 → 그 뒤 FK 순서대로 (OutboxIntegrationTest와 같음)
        notificationRepository.deleteAllInBatch();
        consumedEventRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        paymentRefundRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        menuRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void pay() {
        OrderResponse order = orderService.createOrder(new OrderCreateRequest(
                List.of(new OrderItemRequest(menu.id(), 1)), "06236", "서울 강남구 테헤란로 152", "101호"), "customer1");
        paymentService.pay(new PaymentRequest(order.id(), "CARD"), "customer1");
    }

    @Test
    void Kafka_없이_기동하고_전달_방법은_direct_하나뿐이다() {
        assertThat(senders).singleElement().isInstanceOf(DirectOutboxSender.class);
    }

    @Test
    void 결제_이벤트가_Kafka_없이_알림으로_저장되고_다시_발행해도_중복되지_않는다() {
        pay();

        PublishResult first = outboxPublisher.publishPending();

        assertThat(first).isEqualTo(new PublishResult(1, 0, 0));
        assertThat(outboxEventRepository.findAll()).singleElement()
                .extracting(OutboxEvent::getStatus).isEqualTo(OutboxEventStatus.PUBLISHED);
        // 결제 완료 알림: 고객·사장님 각 1건 (Kafka 경로의 Consumer와 같은 결과)
        assertThat(notificationRepository.findAll()).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(customer.getId(), owner.getId());

        // 발행 결과 기록 전에 실패해 같은 이벤트가 다시 전달되는 상황 → eventId로 걸러져 알림은 그대로
        OutboxEvent published = outboxEventRepository.findAll().getFirst();
        assertThat(senders.getFirst().send(published).join()).isEqualTo(false);
        assertThat(notificationRepository.count()).isEqualTo(2);
        // PUBLISHED는 다시 발행 대상이 아님
        assertThat(outboxPublisher.publishPending()).isEqualTo(new PublishResult(0, 0, 0));
    }

    @Test
    void Actuator는_서비스_포트에서_health만_상세_없이_제공한다() throws Exception {
        assertThat(serverPort).isNotZero();
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(get("/api/stores").statusCode()).isEqualTo(200);
    }

    // 전역 지연 초기화를 쓰지 않으므로 @Scheduled 정산 작업이 기동 시 등록되어야 함 (docs/operations.md 9-3)
    @Test
    void 정산_스케줄러_작업이_기본_cron으로_등록된다() {
        List<CronTask> settlementTasks = scheduledTaskHolders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(scheduled -> scheduled.getTask())
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filter(task -> task.toString().contains("SettlementScheduler.aggregateDailySettlements"))
                .toList();

        assertThat(settlementTasks).singleElement()
                .extracting(CronTask::getExpression).isEqualTo("0 10 1 * * *");
    }
}
