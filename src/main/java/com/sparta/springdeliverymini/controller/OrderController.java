package com.sparta.springdeliverymini.controller;

import com.sparta.springdeliverymini.dto.OrderCreateRequest;
import com.sparta.springdeliverymini.dto.OrderResponse;
import com.sparta.springdeliverymini.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // CUSTOMER 권한 확인은 SecurityConfig에서 토큰의 role로 처리 (OWNER → 403)
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            Authentication authentication) {

        // 주문자는 토큰에서 꺼낸 아이디로 Service에서 조회
        OrderResponse order = orderService.createOrder(request, authentication.getName());

        // 주문 생성 성공 시 201 Created와 생성된 주문 정보 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
