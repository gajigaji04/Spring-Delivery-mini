package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

// OWNER가 주문 상태를 변경할 때 사용하는 요청
// 허용 값: ORDER_ACCEPTED(주문수락), DELIVERY_COMPLETED(배달완료) — 허용 여부는 Service에서 검증
public record OrderStatusUpdateRequest(
        @NotNull(message = "변경할 주문 상태를 입력해야 합니다.")
        OrderStatus status
) {
}
