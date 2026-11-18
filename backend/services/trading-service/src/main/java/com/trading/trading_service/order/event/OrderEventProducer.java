package com.trading.trading_service.order.event;

import com.trading.trading_service.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    /**
     * The symbol is the message key, so all orders for one symbol land on the same partition
     * and are consumed in the order they were created (required for price-time priority).
     */
    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        kafkaTemplate.send(Topics.ORDER_CREATED, event.getSymbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}", event.getOrderId(), ex);
                    } else {
                        log.info("Published OrderCreatedEvent for order {} (partition={}, offset={})",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
