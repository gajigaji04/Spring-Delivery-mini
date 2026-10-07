package com.sparta.springdeliverymini.repository;

import com.sparta.springdeliverymini.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
