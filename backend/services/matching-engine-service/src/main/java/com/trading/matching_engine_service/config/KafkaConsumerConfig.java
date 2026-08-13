package com.trading.matching_engine_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "matching-engine-group");
        
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576);
        
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        config.put(ConsumerConfig.METRICS_RECORDING_LEVEL_CONFIG, "INFO");
        
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (record, exception) -> {
                System.err.println("Error processing record: " + record + ", Error: " + exception.getMessage());
            },
            new org.springframework.util.backoff.FixedBackOff(1000L, 3L) // 3 retries with 1s delay
        ));
        
        factory.setConcurrency(3);
        
        return factory;
    }
}