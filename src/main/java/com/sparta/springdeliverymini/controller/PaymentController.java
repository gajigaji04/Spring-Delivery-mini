package com.sparta.springdeliverymini.controller;

import com.sparta.springdeliverymini.dto.PaymentRequest;
import com.sparta.springdeliverymini.dto.PaymentResponse;
import com.sparta.springdeliverymini.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @Controller는 반환값을 뷰 이름으로 해석하므로 JSON 응답을 위해 @RestController 사용
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // CUSTOMER만 결제 가능
    // CUSTOMER 권한 확인은 SecurityConfig에서 토큰의 role로 처리 (OWNER → 403)
    // 본인 주문 여부·결제 수단·주문 상태 검증은 Service에서 처리
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> pay(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        // 결제자는 토큰에서 꺼낸 아이디로 Service에서 조회
        PaymentResponse payment = paymentService.pay(request, authentication.getName());

        // 결제 성공 시 201 Created와 결제 내역 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
}
