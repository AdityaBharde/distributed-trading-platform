package com.trading.matching_engine_service.service;

import com.trading.matching_engine_service.dto.MatchRequest;
import com.trading.matching_engine_service.dto.MatchResponse;
import com.trading.matching_engine_service.engine.MatchingEngine;
import com.trading.matching_engine_service.model.Order;
import com.trading.matching_engine_service.model.Trade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchingService {
    private final MatchingEngine matchingEngine;

    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    public MatchingService(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }
    public MatchResponse processOrder(MatchRequest request) {
        Order order = new Order();
        order.setOrderId(orderIdGenerator.getAndIncrement());
        order.setSymbol(request.getSymbol());
        order.setSide(request.getSide());
        order.setPrice(request.getPrice());
        order.setQuantity(request.getQuantity());
        order.setTimestamp(System.currentTimeMillis());

        List<Trade> trades = matchingEngine.processOrder(order);

        MatchResponse response = new MatchResponse();
        response.setTrades(trades);
        response.setRemainingQuantity(order.getQuantity());

        return response;
    }
}
