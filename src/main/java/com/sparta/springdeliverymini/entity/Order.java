package com.sparta.springdeliverymini.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 new 금지
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문한 메뉴
    // Menu가 삭제되어도 Order 기록은 남아있어야 하므로 실제 메뉴 Entity를 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    // 주문한 사용자
    // 주문 목록 조회 시 본인의 주문인지 확인하기 위해 사용자 정보 저장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 주문 수량
    @Column(nullable = false)
    private int quantity;

    // 주문 당시의 총 금액
    // 메뉴 가격 × 주문 수량으로 서버에서 계산
    @Column(nullable = false)
    private int totalPrice;

    // 배송 주소
    @Column(nullable = false)
    private String deliveryAddress;

    // 주문 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    public Order(
            Menu menu,
            User user,
            int quantity,
            int totalPrice,
            String deliveryAddress,
            OrderStatus status
    ) {
        this.menu = menu;
        this.user = user;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.deliveryAddress = deliveryAddress;
        this.status = status;
    }

    // 주문 상태 변경
    public void changeStatus(OrderStatus status) {
        this.status = status;
    }
}
