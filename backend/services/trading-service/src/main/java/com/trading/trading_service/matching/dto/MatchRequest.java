package com.trading.trading_service.matching.dto;

import com.trading.trading_service.matching.model.OrderSide;
import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchRequest {
    private String symbol;
    private OrderSide side;
    private BigDecimal price;
    private int quantity;
}
