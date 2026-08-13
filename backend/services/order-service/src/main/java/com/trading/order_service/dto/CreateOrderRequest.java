package com.trading.order_service.dto;

import com.trading.order_service.enums.OrderSide;
import com.trading.order_service.enums.OrderType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    private String symbol;
    private OrderSide side;
    private OrderType orderType;
    private BigDecimal price;
    private Integer quantity;
}
