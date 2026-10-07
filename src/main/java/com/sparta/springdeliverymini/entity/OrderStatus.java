package com.sparta.springdeliverymini.entity;

public enum OrderStatus {
    ORDER_REQUEST,  // 주문요청 — 주문 생성 시 항상 이 상태로 시작
    ORDER_CANCELLED // 주문취소 — 주문요청 상태에서만 변경 가능
}
