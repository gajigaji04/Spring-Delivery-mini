package com.sparta.springdeliverymini.maintenance;

import com.sparta.springdeliverymini.TestcontainersConfiguration;
import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;
import com.sparta.springdeliverymini.entity.OwnerApplicationStatus;
import com.sparta.springdeliverymini.entity.Payment;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.jwt.JwtProvider;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.OutboxEventRepository;
import com.sparta.springdeliverymini.repository.OwnerApplicationRepository;
import com.sparta.springdeliverymini.repository.PaymentRepository;
import com.sparta.springdeliverymini.repository.RefreshTokenRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 배포 데모 데이터 — demo-data.enabled=true면 기동 시 한 번 생성, 다시 실행해도 늘지 않음, 공개 계정은 비밀번호 변경 불가
// 관리자 비밀번호를 주지 않음 → 관리자는 로그인할 수 없는 승인 기록용 계정
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

    @AfterEach
    void tearDown() {
        // FK 순서대로 삭제: payments → orders → menus → stores → owner_applications·refresh_tokens → users
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
        assertThat(ownerApplicationRepository.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getStatus()).isEqualTo(OwnerApplicationStatus.APPROVED));
        assertThat(storeRepository.count()).isEqualTo(2);
        assertThat(menuRepository.count()).isEqualTo(6);
        assertThat(orderRepository.findAll()).extracting(Order::getStatus).containsExactlyInAnyOrder(
                OrderStatus.DELIVERY_COMPLETED, OrderStatus.ORDER_ACCEPTED,
                OrderStatus.PAYMENT_COMPLETED, OrderStatus.ORDER_REQUEST);
        // 결제 금액 = 주문 총액(후라이드+콜라2, 떡볶이2+김밥, 양념), 수수료는 Payment 생성자가 계산
        assertThat(paymentRepository.findAll()).extracting(Payment::getAmount)
                .containsExactlyInAnyOrder(23_000L, 14_000L, 19_000L);
        assertThat(paymentRepository.findAll())
                .allSatisfy(p -> assertThat(p.getFeeAmount() + p.getSettlementAmount()).isEqualTo(p.getAmount()));
        assertThat(paymentRepository.findAll()).extracting(Payment::isGatewayPayment).containsOnly(false);
        // 지난 일을 기록하는 데이터 — 결제 완료 이벤트(알림)를 만들지 않음
        assertThat(outboxEventRepository.count()).isZero();

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
}
