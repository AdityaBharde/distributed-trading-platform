package com.trading.matching_engine_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long orderId;

    private String symbol;

    private OrderSide side;

    private BigDecimal price;

    private int quantity;

    private long timestamp;
}