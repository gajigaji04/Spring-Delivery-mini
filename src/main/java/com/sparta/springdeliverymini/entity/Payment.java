package com.sparta.springdeliverymini.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제한 주문
    // 주문 1건당 결제는 1건만 가능 → order_id에 unique 제약
    // 동시에 같은 주문을 결제하는 요청이 들어와도 DB에서 중복 저장을 막음
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // 결제 금액
    // 요청값이 아닌 주문 총액(Order.totalPrice)을 그대로 저장
    @Column(nullable = false)
    private int amount;

    // 결제 수단 (현재는 CARD만 허용)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    // 결제 시각
    @Column(nullable = false)
    private LocalDateTime paidAt;

    protected Payment() {
    }

    public Payment(Order order, int amount, PaymentMethod paymentMethod) {
        this.order = order;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public int getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
