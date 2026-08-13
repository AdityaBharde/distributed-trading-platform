package com.trading.order_service.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "order-created-topic";

    @Transactional
    public void publishOrderCreatedEvent(Object orderEvent) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(TOPIC, orderEvent);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Order event published successfully: offset={}, partition={}",
                            result.getRecordMetadata().offset(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("Failed to publish order event", ex);
                    throw new RuntimeException("Failed to publish order event", ex);
                }
            });

        } catch (Exception e) {
            log.error("Error publishing order event", e);
            throw new RuntimeException("Failed to publish order event", e);
        }
    }
}