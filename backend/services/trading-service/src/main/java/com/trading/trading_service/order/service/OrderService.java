package com.trading.trading_service.order.service;

import com.trading.trading_service.order.dto.CreateOrderRequest;
import com.trading.trading_service.order.enums.OrderStatus;
import com.trading.trading_service.order.event.OrderCreatedEvent;
import com.trading.trading_service.order.event.OrderEventProducer;
import com.trading.trading_service.order.model.Order;
import com.trading.trading_service.order.repository.OrderRepository;
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
        Order saved = orderRepository.save(order);
        OrderCreatedEvent orderCreatedEvent =new OrderCreatedEvent(
                saved.getId(),
                saved.getSymbol(),
                saved.getSide(),
                saved.getOrderType(),
                saved.getPrice(),
                saved.getQuantity(),
                saved.getCreatedAt()
                );
        orderEventProducer.publishOrderCreatedEvent(orderCreatedEvent);
        return saved;
    }


}
