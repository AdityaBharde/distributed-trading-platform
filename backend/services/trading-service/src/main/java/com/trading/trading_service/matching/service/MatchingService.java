package com.trading.trading_service.matching.service;

import com.trading.trading_service.matching.dto.MatchRequest;
import com.trading.trading_service.matching.dto.MatchResponse;
import com.trading.trading_service.matching.engine.MatchingEngine;
import com.trading.trading_service.matching.model.Order;
import com.trading.trading_service.matching.model.Trade;
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
