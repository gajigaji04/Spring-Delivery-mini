package com.sparta.springdeliverymini.maintenance;

import com.sparta.springdeliverymini.config.DemoDataProperties;
import com.sparta.springdeliverymini.config.PaymentFeeProperties;
import com.sparta.springdeliverymini.entity.DeliveryAddress;
import com.sparta.springdeliverymini.entity.Menu;
import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;
import com.sparta.springdeliverymini.entity.OwnerApplication;
import com.sparta.springdeliverymini.entity.Payment;
import com.sparta.springdeliverymini.entity.PaymentMethod;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.Store;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.OwnerApplicationRepository;
import com.sparta.springdeliverymini.repository.PaymentRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 배포 데모 데이터 (demo-data.enabled=true일 때만 빈 생성) — docs/operations.md 9장 "데모 데이터"
//
// 배포 DB는 새로 만든 빈 DB라 방문자가 볼 매장·주문이 없음 → 기동 시 한 번만 만듦
// - 멱등: 데모 사장님 아이디가 이미 있으면 아무것도 하지 않음 (재배포·재시작마다 늘어나지 않음)
//   여러 인스턴스가 동시에 떠도 users.username UNIQUE가 하나만 성공시킴 → 나머지는 롤백 후 건너뜀
// - 한 트랜잭션: 중간에 실패하면 전부 롤백 → 다음 기동에서 처음부터 다시 (반쯤 만든 데이터가 남지 않음)
// - 화면 흐름과 같은 엔티티 메서드로 만듦 (사장님 전환은 OwnerApplication.approve, 결제는 Payment 생성자의 수수료 계산)
//   단, 결제 완료 이벤트(Outbox)·알림은 만들지 않음 — 지난 일을 기록하는 데이터라 알림이 나가면 안 됨
// - 결제는 모의 결제(PG 없음)로 기록. 정산은 다음 날 정산 스케줄러(또는 관리자 재집계)가 이 결제를 집계
// - 공개 계정(사장님·고객)은 비밀번호·이메일 변경 불가 (UserService, DemoDataProperties.isPublicDemoAccount)
//   관리자는 비공개 비밀번호가 있을 때만 로그인 가능, 없으면 승인 기록용(비밀번호 NULL)
@Slf4j
@Component
@ConditionalOnProperty(name = "demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    // 매장 이름 → 메뉴(이름, 가격)
    private static final Map<String, List<MenuSeed>> STORES = Map.of(
            "데모 치킨", List.of(
                    new MenuSeed("후라이드치킨", 18_000, "바삭한 기본 후라이드"),
                    new MenuSeed("양념치킨", 19_000, "달콤매콤 양념"),
                    new MenuSeed("콜라 1.25L", 2_500, null)),
            "데모 분식", List.of(
                    new MenuSeed("떡볶이", 5_000, "쌀떡 기본 맵기"),
                    new MenuSeed("김밥", 4_000, null),
                    new MenuSeed("순대", 4_500, "내장 포함")));

    private final DemoDataProperties properties;
    private final PaymentFeeProperties feeProperties;
    private final UserRepository userRepository;
    private final OwnerApplicationRepository ownerApplicationRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(properties.ownerUsername())) {
            log.info("데모 데이터가 이미 있어 건너뜀 ({})", properties);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> seed());
            log.info("데모 데이터 생성 완료 ({})", properties);
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 먼저 만듦 (같은 아이디 UNIQUE 위반) — 기동은 계속
            log.info("데모 데이터를 다른 인스턴스가 먼저 만들어 건너뜀");
        }
    }

    void seed() {
        LocalDateTime now = LocalDateTime.now(clock);
        String demoPassword = passwordEncoder.encode(properties.password());

        // 관리자는 기존 아이디가 있으면 그대로 씀 (운영자가 먼저 만든 관리자 — 비밀번호를 바꾸지 않음)
        User admin = userRepository.findByUsername(properties.adminUsername()).orElseGet(() ->
                userRepository.save(new User(properties.adminUsername(),
                        StringUtils.hasText(properties.adminPassword())
                                ? passwordEncoder.encode(properties.adminPassword()) : null,
                        Role.ADMIN)));

        // 사장님: 고객으로 가입 → 전환 신청 → 관리자 승인 (V17 승인제와 같은 경로)
        User owner = userRepository.save(new User(properties.ownerUsername(), demoPassword, Role.CUSTOMER));
        OwnerApplication application = new OwnerApplication(owner, "데모 상회", now.minusMinutes(30));
        application.approve(admin, now.minusMinutes(20));
        ownerApplicationRepository.save(application);

        User customer = userRepository.save(new User(properties.customerUsername(), demoPassword, Role.CUSTOMER));

        Store chicken = null;
        Store snack = null;
        for (Map.Entry<String, List<MenuSeed>> entry : STORES.entrySet()) {
            Store store = storeRepository.save(new Store(entry.getKey(), owner));
            for (MenuSeed seed : entry.getValue()) {
                menuRepository.save(new Menu(seed.name(), seed.price(), seed.description(), store));
            }
            if (entry.getKey().equals("데모 치킨")) {
                chicken = store;
            } else {
                snack = store;
            }
        }
        List<Menu> menus = menuRepository.findAll();

        // 사장님 화면에 상태별 주문이 보이도록: 배달완료 · 수락 · 결제완료(수락 대기) · 주문요청(결제 전)
        DeliveryAddress address = new DeliveryAddress("06236", "서울 강남구 테헤란로 123", "데모빌딩 4층");
        placeOrder(customer, chicken, address, menus, Map.of("후라이드치킨", 1, "콜라 1.25L", 2),
                OrderStatus.DELIVERY_COMPLETED, now.minusMinutes(90));
        placeOrder(customer, snack, address, menus, Map.of("떡볶이", 2, "김밥", 1),
                OrderStatus.ORDER_ACCEPTED, now.minusMinutes(25));
        placeOrder(customer, chicken, address, menus, Map.of("양념치킨", 1),
                OrderStatus.PAYMENT_COMPLETED, now.minusMinutes(5));
        placeOrder(customer, snack, address, menus, Map.of("순대", 1),
                OrderStatus.ORDER_REQUEST, now);
    }

    private void placeOrder(User customer, Store store, DeliveryAddress address, List<Menu> menus,
                            Map<String, Integer> items, OrderStatus target, LocalDateTime paidAt) {
        Order order = new Order(customer, store, address, "데모 주문입니다", OrderStatus.ORDER_REQUEST);
        items.forEach((name, quantity) -> order.addItem(menus.stream()
                .filter(m -> m.getName().equals(name) && m.getStore() == store)
                .findFirst()
                .orElseThrow(), quantity));
        orderRepository.save(order);
        if (target == OrderStatus.ORDER_REQUEST) {
            return;
        }

        order.pay();
        paymentRepository.save(new Payment(order, order.getTotalPrice(),
                store.effectiveFeeRate(feeProperties.rate()), PaymentMethod.CARD, paidAt));
        if (target == OrderStatus.PAYMENT_COMPLETED) {
            return;
        }
        order.accept();
        if (target == OrderStatus.DELIVERY_COMPLETED) {
            order.complete(paidAt.plusMinutes(40));
        }
    }

    private record MenuSeed(String name, long price, String description) {
    }
}
