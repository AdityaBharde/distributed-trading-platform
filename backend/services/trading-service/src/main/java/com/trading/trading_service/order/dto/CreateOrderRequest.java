package com.trading.trading_service.order.dto;

import com.trading.trading_service.order.enums.OrderSide;
import com.trading.trading_service.order.enums.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    @NotBlank
    private String symbol;
    @NotNull
    private OrderSide side;
    @NotNull
    private OrderType orderType;
    @Positive
    private BigDecimal price;
    @NotNull
    @Positive
    private Integer quantity;
}
