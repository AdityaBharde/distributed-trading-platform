package com.trading.trading_service.order.model;

import com.trading.trading_service.order.enums.OrderSide;
import com.trading.trading_service.order.enums.OrderStatus;
import com.trading.trading_service.order.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    private BigDecimal price;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt;

}