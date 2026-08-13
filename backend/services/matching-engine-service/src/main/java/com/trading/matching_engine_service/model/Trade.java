package com.trading.matching_engine_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    private Long tradeId;

    private Long buyOrderId;

    private Long sellOrderId;

    private String symbol;

    private BigDecimal price;

    private int quantity;

    private long timestamp;
}