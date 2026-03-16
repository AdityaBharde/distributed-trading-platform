package com.trading.matching_engine_service.engine;

import com.trading.matching_engine_service.model.Order;

import java.util.Collection;
import java.util.PriorityQueue;

public class OrderBook {

    private final PriorityQueue<Order> bids = new PriorityQueue<>((a, b) -> {
        if (a.getPrice().equals(b.getPrice())) {
            return Long.compare(a.getTimestamp(), b.getTimestamp());
        }
        return b.getPrice().compareTo(a.getPrice());
    });

    private final PriorityQueue<Order> asks = new PriorityQueue<>((a, b) -> {
        if (a.getPrice().equals(b.getPrice())) {
            return Long.compare(a.getTimestamp(), b.getTimestamp());
        }
        return a.getPrice().compareTo(b.getPrice());
    });

    public Collection<Order> getAsks() {
        return asks;
    }

    public Order getBestAsk() {
        return asks.peek();
    }

    public void removeBestAsk() {
        asks.poll();
    }

    public void addBid(Order incomingOrder) {
        bids.offer(incomingOrder);
    }

    public void addAsk(Order incomingOrder) {
        asks.offer(incomingOrder);
    }

    public void removeBestBid() {
        bids.poll();
    }

    public Order getBestBid() {
        return bids.peek();
    }

    public Collection<Order> getBids() {
        return bids;
    }
}