package com.trading.matching_engine_service.dto;

import com.trading.matching_engine_service.model.OrderSide;
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
