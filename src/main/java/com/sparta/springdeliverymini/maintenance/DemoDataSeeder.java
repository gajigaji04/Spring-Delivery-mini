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
import com.sparta.springdeliverymini.entity.Settlement;
import com.sparta.springdeliverymini.entity.SettlementPeriod;
import com.sparta.springdeliverymini.entity.SettlementStatus;
import com.sparta.springdeliverymini.entity.SettlementStatusHistory;
import com.sparta.springdeliverymini.entity.Store;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.OwnerApplicationRepository;
import com.sparta.springdeliverymini.repository.PaymentRepository;
import com.sparta.springdeliverymini.repository.SettlementRepository;
import com.sparta.springdeliverymini.repository.SettlementStatusHistoryRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import com.sparta.springdeliverymini.service.SettlementAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// 배포 데모 데이터 (demo-data.enabled=true일 때만 빈 생성) — docs/operations.md 9장 "데모 데이터"
//
// 배포 DB는 새로 만든 빈 DB라 방문자가 볼 매장·주문이 없음 → 기동 시 한 번만 만듦
// - 멱등: 데모 사장님 아이디가 이미 있으면 아무것도 하지 않음 (재배포·재시작마다 늘어나지 않음)
//   여러 인스턴스가 동시에 떠도 users.username UNIQUE가 하나만 성공시킴 → 나머지는 롤백 후 건너뜀
// - 한 트랜잭션: 중간에 실패하면 전부 롤백 → 다음 기동에서 처음부터 다시 (반쯤 만든 데이터가 남지 않음)
// - 화면 흐름과 같은 엔티티 메서드로 만듦 (사장님 전환은 OwnerApplication.approve, 결제는 Payment 생성자의 수수료 계산)
//   단, 결제 완료 이벤트(Outbox)·알림은 만들지 않음 — 지난 일을 기록하는 데이터라 알림이 나가면 안 됨
// - 결제는 모의 결제(PG 없음)로 기록. 정산은 다음 날 정산 스케줄러(또는 관리자 재집계)가 이 결제를 집계
// - 데모 계정은 비밀번호·이메일 변경 불가 (UserService, DemoDataProperties.isPublicDemoAccount)
//   관리자는 방문자가 체험 로그인 API(DemoLoginService)로 들어옴 — 비공개 비밀번호는 있어도 되고 없어도 됨
//
// 두 단계, 단계마다 표시 아이디로 멱등 (이미 배포된 DB에도 2단계만 더해지도록 나눔)
// 1단계(사장님 아이디): 관리자·사장님·고객·매장·메뉴·오늘 주문 4건
// 2단계(신청자 아이디): 승인 대기 사장님 전환 신청 1건 + 지난 3일 배달완료 주문 → 정산 집계 → 지급·확정·대기로 나눔
//   관리자 화면에서 정산 확정·지급, 신청 승인을 바로 해 볼 수 있도록
//
// 초기화(reset, 관리자 API — service.DemoDataResetService): 방문자가 확정·지급·승인·주문한 결과를 지우고 처음 상태로
// - 데모 회원(관리자·사장님·고객)은 남김 → 지금 로그인한 방문자 세션이 끊기지 않음. 신청자만 지우고 다시 만듦
// - 데모 사장님 매장에 딸린 행(정산·결제·환불·주문·메뉴·매장)만 삭제 → 다른 방문자 매장·주문은 건드리지 않음
//   결제·정산 기록 불변 원칙의 예외: 실제 거래가 아닌 데모 매장 데이터이고, demo-data.enabled=true일 때만 이 빈이 있음
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
    private final SettlementRepository settlementRepository;
    private final SettlementStatusHistoryRepository settlementStatusHistoryRepository;
    private final SettlementAggregationService settlementAggregationService;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbcTemplate; // 초기화 삭제 — 매장 단위로 여러 표를 한 번에 지움
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(properties.ownerUsername())) {
            log.info("데모 기본 데이터가 이미 있어 건너뜀 ({})", properties);
        } else if (inTransaction("기본", () -> {
            seed();
            return true;
        }) == null) {
            return;
        }

        if (userRepository.existsByUsername(properties.applicantUsername())) {
            log.info("데모 정산 데이터가 이미 있어 건너뜀");
            return;
        }
        List<LocalDate> dates = inTransaction("정산용 주문", this::seedHistory);
        if (dates != null) {
            settleHistory(dates);
        }
    }

    // 한 트랜잭션으로 실행 — 다른 인스턴스가 먼저 만들었으면(같은 아이디 UNIQUE 위반) null, 기동은 계속
    private <T> T inTransaction(String name, Supplier<T> work) {
        try {
            T result = transactionTemplate.execute(status -> work.get());
            log.info("데모 {} 데이터 생성 완료 ({})", name, properties);
            return result;
        } catch (DataIntegrityViolationException e) {
            log.info("데모 {} 데이터를 다른 인스턴스가 먼저 만들어 건너뜀", name);
            return null;
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
        seedStores(owner, customer, now);
    }

    // 1단계 중 매장·메뉴·오늘 주문 (초기화 때도 기존 사장님·고객으로 다시 만듦)
    private void seedStores(User owner, User customer, LocalDateTime now) {
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

        // 사장님 화면에 상태별 주문이 보이도록: 배달완료 · 수락(배달 중) · 결제완료(수락 대기) · 주문요청(결제 전)
        //   → 고객은 결제(주문요청)·취소(결제완료)를, 사장님은 수락(결제완료)·배달완료(수락)를 바로 해 볼 수 있음
        //   배달완료 주문은 90분 전 결제 → 50분 전 배달완료 (지금보다 늦은 시각이 생기지 않도록)
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

    // 2단계 ① — 승인 대기 신청 + 정산할 지난 3일 배달완료 주문. 정산할 날짜를 돌려줌
    // 날짜는 데모 매장이 생긴 날보다 앞 → 그 날짜에는 정산이 있을 수 없음
    //   (이미 집계된 날짜에 주문을 더하면 그 주문은 영영 정산되지 않으므로, 이미 배포된 DB에서도 안전한 날짜를 고름)
    List<LocalDate> seedHistory() {
        LocalDateTime now = LocalDateTime.now(clock);
        User owner = userRepository.findByUsername(properties.ownerUsername()).orElseThrow();
        User customer = userRepository.findByUsername(properties.customerUsername()).orElseThrow();

        // 관리자 승인 시연용 — 비밀번호 없는 회원(로그인 불가)이라 방문자가 이 계정으로 들어오지 않음
        User applicant = userRepository.save(new User(properties.applicantUsername(), null, Role.CUSTOMER));
        ownerApplicationRepository.save(new OwnerApplication(applicant, "데모 떡집", now.minusHours(2)));

        List<Store> stores = storeRepository.findAllByOwnerId(owner.getId());
        Store chicken = stores.stream().filter(s -> s.getName().equals("데모 치킨")).findFirst().orElseThrow();
        Store snack = stores.stream().filter(s -> s.getName().equals("데모 분식")).findFirst().orElseThrow();
        List<Menu> menus = menuRepository.findAll().stream()
                .filter(m -> m.getStore() == chicken || m.getStore() == snack)
                .toList();

        LocalDate firstStoreDate = stores.stream()
                .map(store -> store.getCreatedAt().toLocalDate())
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate last = SettlementPeriod.lastConfirmedDate(now);
        if (!last.isBefore(firstStoreDate)) {
            last = firstStoreDate.minusDays(1);
        }
        List<LocalDate> dates = List.of(last.minusDays(2), last.minusDays(1), last);

        DeliveryAddress address = new DeliveryAddress("06236", "서울 강남구 테헤란로 123", "데모빌딩 4층");
        for (LocalDate date : dates) {
            placeOrder(customer, chicken, address, menus, Map.of("후라이드치킨", 1, "콜라 1.25L", 1),
                    OrderStatus.DELIVERY_COMPLETED, date.atTime(LocalTime.of(12, 10)));
            placeOrder(customer, chicken, address, menus, Map.of("양념치킨", 2),
                    OrderStatus.DELIVERY_COMPLETED, date.atTime(LocalTime.of(19, 30)));
            placeOrder(customer, snack, address, menus, Map.of("떡볶이", 1, "순대", 1, "김밥", 2),
                    OrderStatus.DELIVERY_COMPLETED, date.atTime(LocalTime.of(13, 0)));
        }
        return dates;
    }

    // 2단계 ② — 스케줄러와 같은 집계(SettlementAggregationService)로 정산을 만들고, 관리자 화면과 같은 엔티티 메서드로 상태를 나눔
    // 가장 오래된 날 = 지급(PAID), 다음 날 = 확정(CONFIRMED), 마지막 날 = 대기(PENDING) → 관리자가 확정·지급을 직접 해 볼 수 있음
    // 집계는 매장마다 따로 커밋(SettlementAggregationService) → 실패한 매장은 집계 실패 기록에 남고 관리자 재집계로 복구
    private void settleHistory(List<LocalDate> dates) {
        User admin = userRepository.findByUsername(properties.adminUsername()).orElseThrow();
        User owner = userRepository.findByUsername(properties.ownerUsername()).orElseThrow();
        List<Long> storeIds = storeRepository.findIdsByOwnerId(owner.getId());
        for (LocalDate date : dates) {
            for (Long storeId : storeIds) {
                settlementAggregationService.reaggregate(date, storeId, admin);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            for (Long storeId : storeIds) {
                settlementRepository.findByStoreIdAndSettlementDate(storeId, dates.get(0))
                        .ifPresent(settlement -> {
                            confirm(settlement, admin, now.minusMinutes(30));
                            settlement.markPaid(now.minusMinutes(20));
                            settlementStatusHistoryRepository.save(SettlementStatusHistory.paid(settlement, admin));
                        });
                settlementRepository.findByStoreIdAndSettlementDate(storeId, dates.get(1))
                        .ifPresent(settlement -> confirm(settlement, admin, now.minusMinutes(10)));
            }
        });
        log.info("데모 정산 생성 완료 dates={}, storeIds={}", dates, storeIds);
    }

    // 초기화 — 데모 매장 데이터·신청자를 지우고 처음 상태로 다시 만듦 (관리자 API, service.DemoDataResetService)
    // synchronized: 한 인스턴스 안에서 동시 초기화가 겹치지 않도록 (무료 배포는 인스턴스 1개.
    //   여러 인스턴스면 삭제·생성이 겹칠 수 있으나 신청자 아이디 UNIQUE로 2단계는 한쪽만 성공)
    public synchronized void reset() {
        if (!userRepository.existsByUsername(properties.ownerUsername())) {
            run(null); // 기본 데이터가 없으면 처음 시드와 같음
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            purge();
            User owner = userRepository.findByUsername(properties.ownerUsername()).orElseThrow();
            User customer = userRepository.findByUsername(properties.customerUsername()).orElseThrow();
            seedStores(owner, customer, LocalDateTime.now(clock));
        });
        List<LocalDate> dates = inTransaction("정산용 주문", this::seedHistory);
        if (dates != null) {
            settleHistory(dates);
        }
        log.info("데모 데이터 초기화 완료");
    }

    // 데모 사장님 매장에 딸린 행과 신청자를 FK 역순으로 삭제 (docs/erd.md)
    // 결제·정산 쪽은 데모 매장 기준으로만 고름 → 데모 고객이 다른 방문자 매장에 한 주문은 그 매장 정산에 들어가 있을 수 있어 남김
    private void purge() {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("owner", properties.ownerUsername())
                .addValue("applicant", properties.applicantUsername());
        String stores = "SELECT s.id FROM stores s JOIN users u ON u.id = s.owner_id WHERE u.username = :owner";
        String orders = "SELECT o.id FROM orders o WHERE o.store_id IN (" + stores + ")";
        String payments = "SELECT p.id FROM payments p WHERE p.order_id IN (" + orders + ")";
        String settlements = "SELECT st.id FROM settlements st WHERE st.store_id IN (" + stores + ")";
        String applicant = "SELECT u.id FROM users u WHERE u.username = :applicant";
        List<String> statements = List.of(
                "DELETE FROM settlement_carryovers WHERE from_settlement_id IN (" + settlements + ")"
                        + " OR to_settlement_id IN (" + settlements + ")",
                "DELETE FROM settlement_refunds WHERE settlement_id IN (" + settlements + ")"
                        + " OR payment_id IN (" + payments + ")",
                "DELETE FROM settlement_status_histories WHERE settlement_id IN (" + settlements + ")",
                "DELETE FROM settlement_aggregation_failures WHERE store_id IN (" + stores + ")",
                "DELETE FROM settlements WHERE id IN (" + settlements + ")",
                "DELETE FROM pg_cancel_requests WHERE payment_id IN (" + payments + ")",
                "DELETE FROM payment_idempotency_keys WHERE payment_id IN (" + payments + ")",
                "DELETE FROM payment_refunds WHERE payment_id IN (" + payments + ")",
                "DELETE FROM payments WHERE id IN (" + payments + ")",
                "DELETE FROM notifications WHERE order_id IN (" + orders + ")", // FK는 없지만 사라진 주문 알림이 남지 않도록
                "DELETE FROM orders WHERE id IN (" + orders + ")", // order_items는 ON DELETE CASCADE
                "DELETE FROM menus WHERE store_id IN (" + stores + ")",
                "DELETE FROM stores WHERE id IN (" + stores + ")",
                // 신청자: 비밀번호가 없어 로그인할 수 없으므로 토큰 등은 없음 — 신청 행만 지우면 됨
                "DELETE FROM owner_applications WHERE user_id IN (" + applicant + ")",
                "DELETE FROM users WHERE id IN (" + applicant + ")");
        for (String sql : statements) {
            jdbcTemplate.update(sql, params);
        }
    }

    private void confirm(Settlement settlement, User admin, LocalDateTime confirmedAt) {
        if (settlement.getStatus() == SettlementStatus.PENDING) {
            settlement.confirm(confirmedAt);
            settlementStatusHistoryRepository.save(SettlementStatusHistory.confirmed(settlement, admin));
        }
    }

    // at: 결제 시각 (결제 전 주문이면 주문 시각). 시간 순서 = 주문(at-2분) → 결제(at) → 수락 → 배달완료(at+40분)
    private void placeOrder(User customer, Store store, DeliveryAddress address, List<Menu> menus,
                            Map<String, Integer> items, OrderStatus target, LocalDateTime at) {
        Order order = new Order(customer, store, address, "데모 주문입니다", OrderStatus.ORDER_REQUEST);
        items.forEach((name, quantity) -> order.addItem(menus.stream()
                .filter(m -> m.getName().equals(name) && m.getStore() == store)
                .findFirst()
                .orElseThrow(), quantity));
        orderRepository.saveAndFlush(order);
        // 주문 시각(created_at)은 Auditing이 저장 시각(= 지금)으로 채우고 엔티티로는 바꿀 수 없음(updatable = false)
        //   → 그대로 두면 지난 주문이 "결제·배달보다 늦게 주문"된 것으로 보임. 시드 주문만 SQL로 결제 2분 전으로 맞춤
        //   (주문 행은 결제·정산 기록이 아니므로 불변 규칙 대상이 아님. 이후 엔티티 UPDATE에는 created_at이 빠져 유지됨)
        LocalDateTime orderedAt = target == OrderStatus.ORDER_REQUEST ? at : at.minusMinutes(2);
        jdbcTemplate.update("UPDATE orders SET created_at = :orderedAt, updated_at = :orderedAt WHERE id = :id",
                new MapSqlParameterSource().addValue("orderedAt", orderedAt).addValue("id", order.getId()));
        if (target == OrderStatus.ORDER_REQUEST) {
            return;
        }

        order.pay();
        paymentRepository.save(new Payment(order, order.getTotalPrice(),
                store.effectiveFeeRate(feeProperties.rate()), PaymentMethod.CARD, at));
        if (target == OrderStatus.PAYMENT_COMPLETED) {
            return;
        }
        order.accept();
        if (target == OrderStatus.DELIVERY_COMPLETED) {
            order.complete(at.plusMinutes(40));
        }
    }

    private record MenuSeed(String name, long price, String description) {
    }
}
