package com.trading.order_service.event;


import com.trading.order_service.enums.OrderSide;
import com.trading.order_service.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private String symbol;


    private OrderSide side;

    private OrderType orderType;

    private BigDecimal price;

    private Integer quantity;

    private Instant createdAt;
}
