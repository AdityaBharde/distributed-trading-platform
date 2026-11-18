package com.trading.trading_service.matching.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.trading_service.Topics;
import com.trading.trading_service.matching.dto.MatchRequest;
import com.trading.trading_service.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final MatchingService matchingService;
    private final ObjectMapper objectMapper;

    private final Map<String, Boolean> processedMessages = new ConcurrentHashMap<>();

    @KafkaListener(
            topics = Topics.ORDER_CREATED,
            groupId = "matching-engine-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Map<String, Object>> record) {
        try {
            String messageId = generateMessageId(record);

            if (processedMessages.containsKey(messageId)) {
                log.warn("Duplicate message detected, skipping: {}", messageId);
                return;
            }

            log.info("Received order event: offset={}, partition={}, key={}",
                    record.offset(), record.partition(), record.key());

            Map<String, Object> orderData = record.value();

            matchingService.processOrder(objectMapper.convertValue(orderData, MatchRequest.class));

            processedMessages.put(messageId, true);

            if (processedMessages.size() > 10000) {
                processedMessages.clear();
            }

            log.info("Order event processed successfully: {}", messageId);

        } catch (Exception e) {
            log.error("Error processing order event at offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    private String generateMessageId(ConsumerRecord<String, Map<String, Object>> record) {
        return String.format("%s-%d-%d", record.topic(), record.partition(), record.offset());
    }
}