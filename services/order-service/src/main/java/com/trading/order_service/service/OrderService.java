package com.trading.order_service.service;

import com.trading.order_service.dto.CreateOrderRequest;
import com.trading.order_service.enums.OrderStatus;
import com.trading.order_service.event.OrderCreatedEvent;
import com.trading.order_service.event.OrderEventProducer;
import com.trading.order_service.model.Order;
import com.trading.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;


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
        Order order1 = orderRepository.save(order);
        OrderCreatedEvent orderCreatedEvent =new OrderCreatedEvent(
                order1.getId(),
                order1.getSymbol(),
                order1.getSide(),
                order1.getOrderType(),
                order1.getPrice(),
                order1.getQuantity(),
                order1.getCreatedAt()
                );
        orderEventProducer.publishOrderCreatedEvent(orderCreatedEvent);
        return order1;
    }


}
