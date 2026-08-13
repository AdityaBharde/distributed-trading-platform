package com.trading.order_service.controller;

import com.trading.order_service.dto.CreateOrderRequest;
import com.trading.order_service.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public void createOrder(@RequestBody CreateOrderRequest request){
        orderService.createOrder(request);
    }
}
