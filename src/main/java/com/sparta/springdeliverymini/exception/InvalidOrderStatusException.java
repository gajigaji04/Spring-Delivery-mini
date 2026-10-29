package com.sparta.springdeliverymini.exception;

// 현재 주문 상태에서 허용되지 않는 상태 변경을 시도할 때 Order 엔티티가 던지는 예외
// 엔티티가 HttpStatus(웹 계층)를 알지 않도록 ApiException과 분리하고,
// 409 응답 변환은 GlobalExceptionHandler에서 처리
public class InvalidOrderStatusException extends RuntimeException {

    public InvalidOrderStatusException(String message) {
        super(message);
    }
}
