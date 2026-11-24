package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // 주문 목록 정렬 (#98) — 최신 주문이 위. 주문 시각이 같으면 id로 갈라 순서를 고유하게(페이지 사이 중복·누락 방지)
    // id 순서 ≠ 주문 시각 순서일 수 있어(데모 시더가 지난 주문을 나중에 넣고 시각만 과거로 기록) id가 아니라 주문 시각 기준
    // 인덱스 idx_orders_user_id_created_at_id·idx_orders_store_id_created_at_id (V25)
    Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    // 목록 조회는 모두 페이지 단위 (#40) — 정렬(NEWEST_FIRST)과 크기는 PageQuery·KeysetQuery의 toPageable(sort)에서 지정
    // Slice: 전체 개수를 세지 않고 size + 1건만 읽어 다음 페이지 여부를 판단
    // 응답에 매장 이름이 들어가므로 매장을 같은 쿼리에서 함께 조회(@EntityGraph) → 주문마다 매장을 따로 조회하지 않음
    // 주문 항목(1:N)은 fetch join하면 페이징이 메모리에서 처리되므로 Order.orderItems의 @BatchSize로 한 번에 조회

    // CUSTOMER가 본인의 주문만 조회 — 인덱스 idx_orders_user_id_created_at_id (V25)
    @EntityGraph(attributePaths = "store")
    Slice<Order> findAllByUserId(Long userId, Pageable pageable);

    // OWNER가 본인의 모든 매장에 들어온 주문 조회 (orders.store_id → stores.owner_id)
    @EntityGraph(attributePaths = "store")
    Slice<Order> findAllByStoreOwnerId(Long ownerId, Pageable pageable);

    // keyset 페이징 (#54, 정렬 #98) — 커서 주문(afterId)의 (주문 시각, id)보다 목록에서 뒤에 오는 주문만 조회
    //   = 더 이른 주문, 또는 같은 시각이면 id가 더 작은 주문 (Pageable은 첫 페이지 + 크기 + NEWEST_FIRST 정렬만 사용)
    // OFFSET처럼 앞 페이지의 행을 읽고 버리지 않으므로 몇 번째 페이지든 응답 시간이 일정 (docs/performance.md 9장)
    // 커서 주문은 위치로만 쓰임 — 본인 주문이 아니어도 그 위치 다음의 본인 주문부터
    String BEFORE_CURSOR = "(o.createdAt < :createdAt OR (o.createdAt = :createdAt AND o.id < :id))";

    // 고객: 인덱스 idx_orders_user_id_created_at_id (V25)의 user_id 범위를 커서 위치부터 거꾸로 읽음
    @EntityGraph(attributePaths = "store")
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND " + BEFORE_CURSOR)
    Slice<Order> findAllByUserIdBefore(@Param("userId") Long userId, @Param("createdAt") LocalDateTime createdAt,
                                       @Param("id") Long id, Pageable pageable);

    // 점주: 본인 매장(stores.owner_id) 조인 — 인덱스로 개선되지 않는 경로 (docs/performance.md 6장 남은 병목)
    @EntityGraph(attributePaths = "store")
    @Query("SELECT o FROM Order o WHERE o.store.owner.id = :ownerId AND " + BEFORE_CURSOR)
    Slice<Order> findAllByStoreOwnerIdBefore(@Param("ownerId") Long ownerId, @Param("createdAt") LocalDateTime createdAt,
                                             @Param("id") Long id, Pageable pageable);

    // keyset 커서 위치 — 커서 주문의 주문 시각만 읽음 (엔티티 전체를 올리지 않음)
    @Query("SELECT o.createdAt FROM Order o WHERE o.id = :id")
    Optional<LocalDateTime> findCreatedAtById(@Param("id") Long id);

    // 특정 매장에 들어온 주문 조회 (소유권 검증은 Service에서 먼저 처리)
    // 매장은 소유권 검증 때 이미 조회되어 있으므로 함께 조회하지 않음 — 인덱스 idx_orders_store_id_created_at_id (V25)
    Slice<Order> findAllByStoreId(Long storeId, Pageable pageable);

    // 주문 단건 조회용 (#62) — 매장·주문 항목을 한 쿼리로 함께 조회(fetch join)
    // 단건이라 컬렉션 fetch join의 메모리 페이징 문제가 없음 → 항목을 @BatchSize로 따로 읽지 않고 쿼리 1번
    // 소유권 확인(order.user.id, store.owner.id)은 FK 값이라 추가 쿼리 없음
    @Query("SELECT o FROM Order o JOIN FETCH o.store LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    // 상태 변경용 조회 — 주문 행 잠금(SELECT ... FOR UPDATE)
    // 결제·취소·수락·배달완료가 같은 주문에 동시에 들어와도 한 번에 하나씩 처리
    //   → 예) 고객 취소와 사장님 수락이 함께 결제완료 상태를 읽고 둘 다 성공하는 일을 막음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // 사장님 전환 신청·승인 전 확인 (V17) — 진행 중인 주문이 남은 고객은 전환하지 않음
    // 전환 뒤에는 고객 API(취소·결제 조회)가 DB 역할 기준 403이 되어 그 주문을 다룰 수 없게 되므로
    // 인덱스 idx_orders_user_id_created_at_id (V25)의 user_id 범위에서 상태만 확인
    boolean existsByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);
}
