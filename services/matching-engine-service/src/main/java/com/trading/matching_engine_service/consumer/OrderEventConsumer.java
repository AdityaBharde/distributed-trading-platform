package com.trading.matching_engine_service.consumer;

import com.trading.matching_engine_service.dto.MatchRequest;
import com.trading.matching_engine_service.model.OrderSide;
import com.trading.matching_engine_service.service.MatchingService;
import com.trading.order_service.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final MatchingService matchingService;

    public OrderEventConsumer(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @KafkaListener(topics = "order-created-topic", groupId = "matching-engine-group")
    public void consumeOrderEvent(OrderCreatedEvent orderCreatedEvent) {
        log.info("Received OrderCreatedEvent: {}", orderCreatedEvent);

        MatchRequest matchRequest = MatchRequest.builder()
                .symbol(orderCreatedEvent.getSymbol())
                .side(OrderSide.valueOf(orderCreatedEvent.getSide().name()))
                .price(orderCreatedEvent.getPrice())
                .quantity(orderCreatedEvent.getQuantity())
                .build();

        matchingService.processOrder(matchRequest);
    }
}