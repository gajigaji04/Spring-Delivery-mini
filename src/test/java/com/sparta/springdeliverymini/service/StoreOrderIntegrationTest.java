package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.TestcontainersConfiguration;
import com.sparta.springdeliverymini.dto.*;
import com.sparta.springdeliverymini.entity.Role;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.StoreRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 매장 → 메뉴 → 주문 흐름을 실제 PostgreSQL로 검증
// (Mock 테스트로는 확인할 수 없는 매핑·FK·Query Method(findAllByStoreOwnerId 등)의 실제 동작 확인)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StoreOrderIntegrationTest {

    @Autowired private StoreService storeService;
    @Autowired private MenuService menuService;
    @Autowired private OrderService orderService;

    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private OrderRepository orderRepository;

    private StoreResponse chickenStore;
    private StoreResponse pizzaStore;
    private StoreResponse otherOwnerStore;

    // 목록 조회의 기본 페이지 (첫 페이지, 20건)
    private static final PageQuery PAGE = PageQuery.of(0, PageQuery.DEFAULT_SIZE);

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("owner1", "encoded-password", Role.OWNER));
        userRepository.save(new User("owner2", "encoded-password", Role.OWNER));
        userRepository.save(new User("customer1", "encoded-password", Role.CUSTOMER));

        // owner1은 매장 2개, owner2는 매장 1개
        chickenStore = storeService.createStore(new StoreCreateRequest("치킨집"), "owner1");
        pizzaStore = storeService.createStore(new StoreCreateRequest("피자집"), "owner1");
        otherOwnerStore = storeService.createStore(new StoreCreateRequest("분식집"), "owner2");
    }

    @AfterEach
    void tearDown() {
        // FK 순서대로 삭제: orders(order_items는 ON DELETE CASCADE로 함께 삭제) → menus → stores → users
        orderRepository.deleteAllInBatch();
        menuRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private OrderResponse order(StoreResponse store, String menuName, String ownerName) {
        MenuResponse menu = menuService.createMenu(
                new MenuCreateRequest(store.id(), menuName, 10000L, null), ownerName);
        return orderService.createOrder(
                new OrderCreateRequest(List.of(new OrderItemRequest(menu.id(), 1)), "06236", "서울 강남구 테헤란로 152", "101호"), "customer1");
    }

    @Test
    void 여러_메뉴를_담은_주문은_항목별로_저장되고_메뉴가_바뀌어도_주문_당시_값이_유지된다() {
        MenuResponse chicken = menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "후라이드 치킨", 18000L, null), "owner1");
        MenuResponse coke = menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "콜라", 2000L, null), "owner1");

        OrderResponse created = orderService.createOrder(new OrderCreateRequest(List.of(
                new OrderItemRequest(chicken.id(), 2),
                new OrderItemRequest(coke.id(), 3)), "06236", "서울 강남구 테헤란로 152", "101호"), "customer1");

        // 주문 후 메뉴 이름·가격 변경, 메뉴 삭제
        menuService.putMenu(chicken.id(), new MenuUpdateRequest("크리스피 치킨", 20000L, null), "owner1");
        menuService.deleteMenu(coke.id(), "owner1");

        // DB에서 다시 읽어도 주문 당시 이름·단가와 총액 그대로
        OrderResponse reloaded = orderService.getOrders("customer1", PAGE).getContent().get(0);
        assertThat(reloaded.id()).isEqualTo(created.id());
        assertThat(reloaded.totalPrice()).isEqualTo(18000L * 2 + 2000L * 3);
        assertThat(reloaded.items())
                .extracting(OrderItemResponse::menuName, OrderItemResponse::unitPrice, OrderItemResponse::quantity)
                .containsExactlyInAnyOrder(
                        tuple("후라이드 치킨", 18000L, 2),
                        tuple("콜라", 2000L, 3));
    }

    @Test
    void 다른_매장의_메뉴를_함께_담으면_400이고_주문은_저장되지_않는다() {
        MenuResponse chicken = menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "후라이드 치킨", 18000L, null), "owner1");
        MenuResponse pizza = menuService.createMenu(
                new MenuCreateRequest(pizzaStore.id(), "치즈 피자", 20000L, null), "owner1");

        assertThatThrownBy(() -> orderService.createOrder(new OrderCreateRequest(List.of(
                new OrderItemRequest(chicken.id(), 1),
                new OrderItemRequest(pizza.id(), 1)), "06236", "서울 강남구 테헤란로 152", "101호"), "customer1"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void 주문은_메뉴의_매장으로_저장된다() {
        OrderResponse order = order(chickenStore, "후라이드 치킨", "owner1");

        assertThat(order.storeId()).isEqualTo(chickenStore.id());
        assertThat(order.storeName()).isEqualTo("치킨집");
    }

    // ===== 목록 페이징 (#40) =====

    @Test
    void 주문_목록은_최신_주문부터_페이지_단위로_조회되고_페이지끼리_겹치지_않는다() {
        List<Long> created = List.of(
                order(chickenStore, "후라이드 치킨", "owner1").id(),
                order(pizzaStore, "치즈 피자", "owner1").id(),
                order(chickenStore, "양념 치킨", "owner1").id(),
                order(otherOwnerStore, "떡볶이", "owner2").id(),
                order(pizzaStore, "페퍼로니 피자", "owner1").id());

        Slice<OrderResponse> first = orderService.getOrders("customer1", PageQuery.of(0, 2));
        Slice<OrderResponse> second = orderService.getOrders("customer1", PageQuery.of(1, 2));
        Slice<OrderResponse> last = orderService.getOrders("customer1", PageQuery.of(2, 2));

        assertThat(first).extracting(OrderResponse::id).containsExactly(created.get(4), created.get(3));
        assertThat(second).extracting(OrderResponse::id).containsExactly(created.get(2), created.get(1));
        assertThat(last).extracting(OrderResponse::id).containsExactly(created.get(0));
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
        // 범위를 넘은 페이지는 빈 배열
        assertThat(orderService.getOrders("customer1", PageQuery.of(3, 2))).isEmpty();
    }

    @Test
    void 주문_목록은_주문_수와_상관없이_쿼리_3번으로_조회한다() {
        // 개선 전: 주문마다 매장 1번 + 주문 항목 1번씩 추가 조회 (N+1)
        // 개선 후: 회원 1 + 주문·매장(join) 1 + 주문 항목(IN) 1
        for (int i = 0; i < 4; i++) {
            order(chickenStore, "치킨 " + i, "owner1");
            order(pizzaStore, "피자 " + i, "owner1");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Slice<OrderResponse> customerOrders = orderService.getOrders("customer1", PAGE);
        long customerQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        Slice<OrderResponse> ownerOrders = orderService.getOrders("owner1", PAGE);
        long ownerQueries = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        assertThat(customerOrders).hasSize(8).allSatisfy(order -> {
            assertThat(order.storeName()).isNotBlank();
            assertThat(order.items()).hasSize(1);
        });
        assertThat(ownerOrders).hasSize(8);
        assertThat(customerQueries).isEqualTo(3);
        assertThat(ownerQueries).isEqualTo(3);
    }

    // 점주 전체 주문을 keyset으로 끝까지 넘김 — X-Next-After-Id 대신 마지막 id를 그대로 다음 afterId로 사용
    private List<List<Long>> keysetPages(String username, int size) {
        List<List<Long>> pages = new ArrayList<>();
        long afterId = 0;
        while (true) {
            Slice<OrderResponse> slice = orderService.getOrdersAfter(username, KeysetQuery.of(afterId, size));
            List<Long> ids = slice.getContent().stream().map(OrderResponse::id).toList();
            pages.add(ids);
            if (!slice.hasNext()) {
                return pages;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    @Test
    void 점주_keyset_페이징은_OFFSET과_같은_순서로_중복_누락_없이_끝까지_조회된다() {
        // owner1의 주문 사이사이에 owner2의 주문을 섞어 id가 연속되지 않게 함
        List<Long> owner1Orders = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            owner1Orders.add(order(i % 2 == 0 ? chickenStore : pizzaStore, "메뉴 " + i, "owner1").id());
            order(otherOwnerStore, "분식 " + i, "owner2");
        }

        List<List<Long>> pages = keysetPages("owner1", 3);

        // 3 + 3 + 1 — 마지막 페이지에서만 hasNext=false (keysetPages가 그때 멈춤)
        assertThat(pages).extracting(List::size).containsExactly(3, 3, 1);
        assertThat(pages.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(owner1Orders.reversed());
        // 같은 크기의 OFFSET 페이지와 순서·내용이 같음
        for (int page = 0; page < pages.size(); page++) {
            assertThat(orderService.getOrders("owner1", PageQuery.of(page, 3)))
                    .extracting(OrderResponse::id).containsExactlyElementsOf(pages.get(page));
        }
    }

    @Test
    void keyset_경계_afterId가_0이면_첫_페이지_목록_마지막_주문이면_빈_결과_다른_점주의_주문이어도_그_위치_다음부터_조회된다() {
        long first = order(chickenStore, "후라이드", "owner1").id();
        long otherOwners = order(otherOwnerStore, "떡볶이", "owner2").id();
        long second = order(pizzaStore, "피자", "owner1").id();

        // 최신 주문이 위
        assertThat(orderService.getOrdersAfter("owner1", KeysetQuery.of(0, 20)))
                .extracting(OrderResponse::id).containsExactly(second, first);
        // 본인 주문이 아닌 주문(다른 점주 주문)도 위치로만 쓰이므로 그보다 이른 본인 주문부터
        assertThat(orderService.getOrdersAfter("owner1", KeysetQuery.of(otherOwners, 20)))
                .extracting(OrderResponse::id).containsExactly(first);
        Slice<OrderResponse> afterLast = orderService.getOrdersAfter("owner1", KeysetQuery.of(first, 20));
        assertThat(afterLast).isEmpty();
        assertThat(afterLast.hasNext()).isFalse();
        // 정확히 size건만 남았을 때 hasNext=false (size + 1건 조회로 판단)
        Slice<OrderResponse> exact = orderService.getOrdersAfter("owner1", KeysetQuery.of(0, 2));
        assertThat(exact).hasSize(2);
        assertThat(exact.hasNext()).isFalse();
    }

    @Test
    void keyset_페이징_중에_새_주문이_들어와도_이미_받은_주문이_다시_나오지_않고_다음_페이지가_밀리지_않는다() {
        List<Long> before = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            before.add(order(chickenStore, "치킨 " + i, "owner1").id());
        }
        Slice<OrderResponse> first = orderService.getOrdersAfter("owner1", KeysetQuery.of(0, 2));

        long added = order(pizzaStore, "새 피자", "owner1").id();
        long afterId = first.getContent().get(1).id();
        Slice<OrderResponse> second = orderService.getOrdersAfter("owner1", KeysetQuery.of(afterId, 2));
        Slice<OrderResponse> third = orderService.getOrdersAfter(
                "owner1", KeysetQuery.of(second.getContent().get(1).id(), 2));

        // 최신순이라 새 주문은 목록 맨 위(다음 첫 페이지 조회)에 나오고, 이어 받는 페이지에는 섞이지 않음
        //   OFFSET이었다면 새 주문만큼 행이 밀려 before.get(1)이 두 번째 페이지에 다시 나옴
        assertThat(first).extracting(OrderResponse::id).containsExactly(before.get(3), before.get(2));
        assertThat(second).extracting(OrderResponse::id).containsExactly(before.get(1), before.get(0));
        assertThat(third).isEmpty();
        assertThat(orderService.getOrdersAfter("owner1", KeysetQuery.of(0, 1)))
                .extracting(OrderResponse::id).containsExactly(added);
    }

    @Test
    void 주문_시각이_id_순서와_달라도_OFFSET과_keyset_모두_주문_시각_최신순으로_조회된다() {
        // 데모 시더처럼 지난 주문을 나중에 넣고 시각만 과거로 기록한 경우 — id 순서로 정렬하면 사흘 전 주문이 맨 위에 옴
        long older = order(chickenStore, "후라이드 치킨", "owner1").id();
        long newer = order(pizzaStore, "치즈 피자", "owner1").id();
        long backdated = order(chickenStore, "양념 치킨", "owner1").id();
        jdbcTemplate.update("UPDATE orders SET created_at = created_at - INTERVAL '3 days' WHERE id = ?", backdated);

        assertThat(orderService.getOrders("customer1", PageQuery.of(0, 20)))
                .extracting(OrderResponse::id).containsExactly(newer, older, backdated);
        assertThat(keysetPages("owner1", 1)).containsExactly(List.of(newer), List.of(older), List.of(backdated));
        assertThat(orderService.getStoreOrders(chickenStore.id(), "owner1", PageQuery.of(0, 20)))
                .extracting(OrderResponse::id).containsExactly(older, backdated);
    }

    @Test
    void keyset_고객_조회는_본인_주문만_포함하고_쿼리_3번으로_조회한다() {
        for (int i = 0; i < 4; i++) {
            order(chickenStore, "치킨 " + i, "owner1");
            order(otherOwnerStore, "분식 " + i, "owner2");
        }
        userRepository.save(new User("customer2", "encoded-password", Role.CUSTOMER));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Slice<OrderResponse> customerOrders = orderService.getOrdersAfter("customer1", KeysetQuery.of(0, 20));
        long customerQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        Slice<OrderResponse> ownerOrders = orderService.getOrdersAfter("owner1", KeysetQuery.of(0, 20));
        long ownerQueries = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        assertThat(customerOrders).hasSize(8).allSatisfy(order -> assertThat(order.items()).hasSize(1));
        assertThat(ownerOrders).hasSize(4).allSatisfy(order -> assertThat(order.storeName()).isEqualTo("치킨집"));
        assertThat(customerQueries).isEqualTo(3);
        assertThat(ownerQueries).isEqualTo(3);
        assertThat(orderService.getOrdersAfter("customer2", KeysetQuery.of(0, 20))).isEmpty();
    }

    @Test
    void 매장과_메뉴_목록도_id_오름차순으로_페이지_단위로_조회되고_삭제된_메뉴는_빠진다() {
        Slice<StoreResponse> stores = storeService.getStores(PageQuery.of(0, 2));
        assertThat(stores).extracting(StoreResponse::id).containsExactly(chickenStore.id(), pizzaStore.id());
        assertThat(stores.hasNext()).isTrue();
        assertThat(storeService.getStores(PageQuery.of(1, 2)))
                .extracting(StoreResponse::id).containsExactly(otherOwnerStore.id());

        MenuResponse chicken = menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "후라이드 치킨", 18000L, null), "owner1");
        MenuResponse deleted = menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "단종 메뉴", 1000L, null), "owner1");
        MenuResponse pizza = menuService.createMenu(
                new MenuCreateRequest(pizzaStore.id(), "치즈 피자", 20000L, null), "owner1");
        menuService.deleteMenu(deleted.id(), "owner1");

        Slice<MenuResponse> menus = menuService.getMenus(PageQuery.of(0, 2));
        assertThat(menus).extracting(MenuResponse::id).containsExactly(chicken.id(), pizza.id());
        assertThat(menus.hasNext()).isFalse();
    }

    @Test
    void 매장별_주문은_해당_매장의_주문만_조회된다() {
        OrderResponse chickenOrder = order(chickenStore, "후라이드 치킨", "owner1");
        order(pizzaStore, "치즈 피자", "owner1");
        order(otherOwnerStore, "떡볶이", "owner2");

        List<OrderResponse> orders = orderService.getStoreOrders(chickenStore.id(), "owner1", PAGE).getContent();

        assertThat(orders).extracting(OrderResponse::id).containsExactly(chickenOrder.id());
    }

    @Test
    void OWNER_전체_주문_조회는_본인의_모든_매장_주문만_포함한다() {
        order(chickenStore, "후라이드 치킨", "owner1");
        order(pizzaStore, "치즈 피자", "owner1");
        order(otherOwnerStore, "떡볶이", "owner2");

        List<OrderResponse> orders = orderService.getOrders("owner1", PAGE).getContent();

        assertThat(orders).extracting(OrderResponse::storeId)
                .containsExactlyInAnyOrder(chickenStore.id(), pizzaStore.id());
    }

    @Test
    void 다른_OWNER의_매장_주문은_조회할_수_없다() {
        order(chickenStore, "후라이드 치킨", "owner1");

        assertThatThrownBy(() -> orderService.getStoreOrders(chickenStore.id(), "owner2", PAGE))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 다른_OWNER의_매장에는_메뉴를_등록할_수_없다() {
        assertThatThrownBy(() -> menuService.createMenu(
                new MenuCreateRequest(chickenStore.id(), "몰래 등록", 10000L, null), "owner2"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }
}
