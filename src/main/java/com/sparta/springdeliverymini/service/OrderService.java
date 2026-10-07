package com.sparta.springdeliverymini.service;

import com.sparta.springdeliverymini.dto.OrderCreateRequest;
import com.sparta.springdeliverymini.dto.OrderResponse;
import com.sparta.springdeliverymini.entity.Menu;
import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;
import com.sparta.springdeliverymini.entity.User;
import com.sparta.springdeliverymini.exception.ApiException;
import com.sparta.springdeliverymini.repository.MenuRepository;
import com.sparta.springdeliverymini.repository.OrderRepository;
import com.sparta.springdeliverymini.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, MenuRepository menuRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, String username) {

        // 1. 주문자는 요청 본문이 아니라 토큰의 아이디로 찾음
        // 토큰은 유효하지만 그 사이 회원이 삭제된 경우 → 401
        User customer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));

        // 2. 주문할 메뉴 찾기
        // 존재하지 않거나 삭제된 메뉴 → 404
        Menu menu = menuRepository.findByIdAndDeletedFalse(request.menuId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "메뉴가 없습니다."));

        // 3. 총액 = 메뉴 가격 × 수량 (서버에서 계산)
        // int 범위를 넘으면 잘못된 값이 저장되지 않도록 400
        int totalPrice;
        try {
            totalPrice = Math.multiplyExact(menu.getPrice(), request.quantity());
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "주문 금액이 너무 큽니다. 수량을 줄여주세요.");
        }

        // 4. 처음 생성되는 주문은 항상 주문요청 상태
        Order order = new Order(
                menu,
                customer,
                request.quantity(),
                totalPrice,
                request.deliveryAddress(),
                OrderStatus.REQUESTED
        );

        return OrderResponse.from(orderRepository.save(order));
    }
}
