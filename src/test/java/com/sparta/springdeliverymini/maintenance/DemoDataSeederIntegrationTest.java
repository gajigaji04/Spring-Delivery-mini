package com.sparta.springdeliverymini.maintenance;

import com.sparta.springdeliverymini.TestcontainersConfiguration;
import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;
import com.sparta.springdeliverymini.entity.OwnerApplicationStatus;
import com.sparta.springdeliverymini.entity.Payment;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.Settlement;
import com.sparta.springdeliverymini.entity.SettlementStatus;
import com.sparta.springdeliverymini.entity.Store;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.jwt.JwtProvider;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.OutboxEventRepository;
import com.sparta.springdeliverymini.repository.OwnerApplicationRepository;
import com.sparta.springdeliverymini.repository.PaymentRepository;
import com.sparta.springdeliverymini.repository.RefreshTokenRepository;
import com.sparta.springdeliverymini.repository.SettlementAggregationFailureRepository;
import com.sparta.springdeliverymini.repository.SettlementRepository;
import com.sparta.springdeliverymini.repository.SettlementStatusHistoryRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 배포 데모 데이터 — demo-data.enabled=true면 기동 시 한 번 생성, 다시 실행해도 늘지 않음, 공개 계정은 비밀번호 변경 불가
// 관리자 비밀번호를 주지 않음 → 관리자는 체험 로그인 API로만 들어오고, 데모 데이터만 다룸
// 2단계: 승인 대기 신청 + 지난 3일 정산(지급·확정·대기)
@SpringBootTest(properties = {"demo-data.enabled=true", "demo-data.password=demo-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoDataSeederIntegrationTest {

    @Autowired private DemoDataSeeder seeder;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerApplicationRepository ownerApplicationRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private SettlementStatusHistoryRepository settlementStatusHistoryRepository;
    @Autowired private SettlementAggregationFailureRepository settlementAggregationFailureRepository;

    // 기동 때 한 번 시드되지만 테스트마다 tearDown이 지우므로 다시 만듦 (멱등이라 첫 테스트에서는 그대로)
    @BeforeEach
    void seed() {
        seeder.run(null);
    }

    @AfterEach
    void tearDown() {
        // FK 순서대로 삭제: 정산 이력·실패 기록 → settlements → payments → orders → menus → stores
        //                  → owner_applications·refresh_tokens → users
        settlementStatusHistoryRepository.deleteAllInBatch();
        settlementAggregationFailureRepository.deleteAllInBatch();
        settlementRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        menuRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        ownerApplicationRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 기동_시_데모_회원_매장_메뉴_주문을_한_번만_만들고_공개_계정은_비밀번호를_바꿀_수_없다() throws Exception {
        // 기동 때 이미 실행됨 → 다시 실행해도 그대로
        seeder.run(null);

        User owner = userRepository.findByUsername("demoowner").orElseThrow();
        User admin = userRepository.findByUsername("demoadmin").orElseThrow();
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.hasPassword()).isFalse();
        // 1단계 사장님 승인(APPROVED) + 2단계 승인 대기 신청(REQUESTED)
        assertThat(ownerApplicationRepository.findAll()).extracting(a -> a.getStatus())
                .containsExactlyInAnyOrder(OwnerApplicationStatus.APPROVED, OwnerApplicationStatus.REQUESTED);
        assertThat(userRepository.findByUsername("demoapplicant").orElseThrow().hasPassword()).isFalse();
        assertThat(storeRepository.count()).isEqualTo(2);
        assertThat(menuRepository.count()).isEqualTo(6);
        // 오늘 주문 4건 + 지난 3일 × 3건(배달완료)
        assertThat(orderRepository.findAll()).extracting(Order::getStatus)
                .filteredOn(status -> status != OrderStatus.DELIVERY_COMPLETED)
                .containsExactlyInAnyOrder(OrderStatus.ORDER_ACCEPTED, OrderStatus.PAYMENT_COMPLETED, OrderStatus.ORDER_REQUEST);
        assertThat(orderRepository.count()).isEqualTo(13);
        // 오늘 결제 = 주문 총액(후라이드+콜라2, 떡볶이2+김밥, 양념), 수수료는 Payment 생성자가 계산
        assertThat(paymentRepository.findAll()).extracting(Payment::getAmount)
                .contains(23_000L, 14_000L, 19_000L).hasSize(12);
        assertThat(paymentRepository.findAll())
                .allSatisfy(p -> assertThat(p.getFeeAmount() + p.getSettlementAmount()).isEqualTo(p.getAmount()));
        assertThat(paymentRepository.findAll()).extracting(Payment::isGatewayPayment).containsOnly(false);
        // 지난 일을 기록하는 데이터 — 결제 완료 이벤트(알림)를 만들지 않음
        assertThat(outboxEventRepository.count()).isZero();

        // 정산: 매장 2곳 × 3일, 가장 오래된 날 지급 · 다음 날 확정 · 마지막 날 대기
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(6);
        LocalDate first = settlements.stream().map(Settlement::getSettlementDate).min(LocalDate::compareTo).orElseThrow();
        assertThat(settlements).allSatisfy(settlement -> assertThat(settlement.getStatus()).isEqualTo(
                switch ((int) (settlement.getSettlementDate().toEpochDay() - first.toEpochDay())) {
                    case 0 -> SettlementStatus.PAID;
                    case 1 -> SettlementStatus.CONFIRMED;
                    default -> SettlementStatus.PENDING;
                }));
        // 치킨 하루치: 후라이드+콜라(20,500) + 양념 2(38,000) = 58,500 / 분식: 떡볶이+순대+김밥 2(17,500)
        assertThat(settlements).extracting(Settlement::getTotalAmount).containsOnly(58_500L, 17_500L);

        // 공개 계정으로 로그인은 되지만 비밀번호는 못 바꿈
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"democustomer\",\"password\":\"demo-pass-123\"}"))
                .andExpect(status().isOk());
        String bearer = "Bearer " + jwtProvider.createToken("democustomer", Role.CUSTOMER);
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.demoAccount").value(true));
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"demo-pass-123\",\"newPassword\":\"hijacked-123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_PROTECTED"));
    }

    @Test
    void 체험_계정_목록은_관리자_비밀번호를_내려주지_않고_관리자는_비밀번호_없이_체험_로그인한다() throws Exception {
        mockMvc.perform(get("/api/users/demo-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].username").value("democustomer"))
                .andExpect(jsonPath("$[0].password").value("demo-pass-123"))
                .andExpect(jsonPath("$[2].role").value("ADMIN"))
                .andExpect(jsonPath("$[2].password").doesNotExist());

        mockMvc.perform(post("/api/users/demo-login/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().exists("refresh_token"));
    }

    @Test
    void 데모_관리자는_데모_데이터만_보고_다른_매장_정산이나_Outbox는_다룰_수_없다() throws Exception {
        String bearer = "Bearer " + jwtProvider.createToken("demoadmin", Role.ADMIN);
        LocalDate pendingDate = settlementRepository.findAll().stream()
                .map(Settlement::getSettlementDate).max(LocalDate::compareTo).orElseThrow();

        // 데모 매장 정산은 보이고 확정할 수 있음
        mockMvc.perform(get("/api/admin/settlements").param("date", pendingDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        Long pendingId = settlementRepository.findAll().stream()
                .filter(s -> s.getStatus() == SettlementStatus.PENDING).findFirst().orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/settlements/{id}/confirm", pendingId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        // 승인 대기 목록에 데모 신청자만
        mockMvc.perform(get("/api/admin/owner-applications").param("status", "REQUESTED")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("demoapplicant"));

        // 시스템 전체 이벤트는 막힘, 매장 미지정 재집계도 막힘
        mockMvc.perform(get("/api/admin/outbox-events").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ADMIN_SCOPE"));
        mockMvc.perform(post("/api/admin/settlements/reaggregate").param("date", pendingDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void 초기화하면_방문자가_처리한_정산_신청_주문이_처음_상태로_돌아오고_데모_회원은_남는다() throws Exception {
        String bearer = "Bearer " + jwtProvider.createToken("demoadmin", Role.ADMIN);
        Long adminId = userRepository.findByUsername("demoadmin").orElseThrow().getId();
        // 다른 방문자의 매장 — 초기화 범위 밖
        User visitor = userRepository.save(new User("visitor1", "pw", Role.OWNER));
        Store visitorStore = storeRepository.save(new Store("방문자 매장", visitor));
        // 방문자가 처리한 상태: 대기 정산 확정, 대기 신청 승인
        Long pendingId = settlementRepository.findAll().stream()
                .filter(s -> s.getStatus() == SettlementStatus.PENDING).findFirst().orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/settlements/{id}/confirm", pendingId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        Long applicationId = ownerApplicationRepository.findAll().stream()
                .filter(a -> a.getStatus() == OwnerApplicationStatus.REQUESTED).findFirst().orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/owner-applications/{id}/approve", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/demo-data/reset").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetAt").isNotEmpty());

        // 정산 6건이 다시 지급·확정·대기 2건씩, 신청은 다시 대기, 주문·매장 수는 처음과 같음
        assertThat(settlementRepository.findAll()).extracting(Settlement::getStatus).containsExactlyInAnyOrder(
                SettlementStatus.PAID, SettlementStatus.PAID, SettlementStatus.CONFIRMED, SettlementStatus.CONFIRMED,
                SettlementStatus.PENDING, SettlementStatus.PENDING);
        assertThat(ownerApplicationRepository.findAll()).extracting(a -> a.getStatus())
                .containsExactlyInAnyOrder(OwnerApplicationStatus.APPROVED, OwnerApplicationStatus.REQUESTED);
        assertThat(userRepository.findByUsername("demoapplicant").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(storeRepository.count()).isEqualTo(3);
        assertThat(storeRepository.findById(visitorStore.getId())).isPresent();
        assertThat(menuRepository.count()).isEqualTo(6);
        assertThat(orderRepository.count()).isEqualTo(13);
        // 데모 관리자는 같은 회원 그대로 (로그인 중인 방문자 세션이 끊기지 않음)
        assertThat(userRepository.findByUsername("demoadmin").orElseThrow().getId()).isEqualTo(adminId);

        // 1분 안에 다시 요청하면 429
        mockMvc.perform(post("/api/admin/demo-data/reset").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DEMO_RESET_TOO_FREQUENT"));
    }
}
