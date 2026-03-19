package com.trading.order_service.event;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    public OrderEventProducer(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    public void publishOrderCreatedEvent(OrderCreatedEvent orderCreatedEvent){
        kafkaTemplate.send("order-created-topic",orderCreatedEvent);
    }
}
