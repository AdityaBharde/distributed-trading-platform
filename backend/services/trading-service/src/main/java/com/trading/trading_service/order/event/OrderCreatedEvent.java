package com.trading.trading_service.order.event;


import com.trading.trading_service.order.enums.OrderSide;
import com.trading.trading_service.order.enums.OrderType;
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
