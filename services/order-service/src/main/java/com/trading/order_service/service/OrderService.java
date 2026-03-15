package com.trading.order_service.service;

import com.trading.order_service.dto.CreateOrderRequest;
import com.trading.order_service.enums.OrderStatus;
import com.trading.order_service.model.Order;
import com.trading.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .symbol(request.getSymbol())
                .side(request.getSide())
                .orderType(request.getOrderType())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .build();

        return orderRepository.save(order);
    }

}
