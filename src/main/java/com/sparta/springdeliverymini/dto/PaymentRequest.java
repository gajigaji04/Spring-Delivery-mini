package com.sparta.springdeliverymini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 결제 금액은 요청으로 받지 않고 서버에서 주문 총액(totalPrice)을 그대로 사용
// paymentMethod를 enum이 아닌 String으로 받는 이유:
//   enum이면 "CASH" 같은 값이 JSON 변환 단계에서 실패해 "role은 ..." 공통 메시지로 응답됨
//   → String으로 받아 Service에서 "카드만 가능" 메시지로 400 반환
public record PaymentRequest(
        @NotNull(message = "주문 ID를 입력해야 합니다.")
        Long orderId,

        @NotBlank(message = "결제 수단을 입력해야 합니다.")
        String paymentMethod
) {
}
