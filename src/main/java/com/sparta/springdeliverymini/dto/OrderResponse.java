package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Order;
import com.sparta.springdeliverymini.entity.OrderStatus;

public record OrderResponse(
        Long id,
        Long menuId,
        String menuName,
        int quantity,
        int totalPrice,
        String deliveryAddress,
        OrderStatus status) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMenu().getId(),
                order.getMenu().getName(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getDeliveryAddress(),
                order.getStatus()
        );
    }
}
