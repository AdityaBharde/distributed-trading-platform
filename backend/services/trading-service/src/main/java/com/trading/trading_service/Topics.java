package com.trading.trading_service;

/** Kafka topic names shared by the order (producer) and matching (consumer) modules. */
public final class Topics {

    public static final String ORDER_CREATED = "order-created-topic";

    private Topics() {
    }
}
