package com.trading.matching_engine_service.engine;

import com.trading.matching_engine_service.model.Order;
import com.trading.matching_engine_service.model.OrderSide;
import com.trading.matching_engine_service.model.Trade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MatchingEngine {

    private final OrderBook orderBook = new OrderBook();

    public List<Trade> processOrder(Order incomingOrder) {

        List<Trade> trades = new ArrayList<>();

        if (incomingOrder.getSide() == OrderSide.BUY) {

            while (!orderBook.getAsks().isEmpty()
                    && incomingOrder.getPrice().compareTo(orderBook.getBestAsk().getPrice()) >= 0
                    && incomingOrder.getQuantity() > 0) {

                Order bestAsk = orderBook.getBestAsk();

                int tradeQty = Math.min(incomingOrder.getQuantity(), bestAsk.getQuantity());

                Trade trade = new Trade(
                        null,
                        incomingOrder.getOrderId(),
                        bestAsk.getOrderId(),
                        incomingOrder.getSymbol(),
                        bestAsk.getPrice(),
                        tradeQty,
                        System.currentTimeMillis()
                );

                trades.add(trade);

                incomingOrder.setQuantity(incomingOrder.getQuantity() - tradeQty);
                bestAsk.setQuantity(bestAsk.getQuantity() - tradeQty);

                if (bestAsk.getQuantity() == 0) {
                    orderBook.removeBestAsk();
                }
            }

            if (incomingOrder.getQuantity() > 0) {
                orderBook.addBid(incomingOrder);
            }

        } else {

            while (!orderBook.getBids().isEmpty()
                    && incomingOrder.getPrice().compareTo(orderBook.getBestBid().getPrice()) <= 0
                    && incomingOrder.getQuantity() > 0) {

                Order bestBid = orderBook.getBestBid();
                int tradeQty = Math.min(incomingOrder.getQuantity(), bestBid.getQuantity());
                Trade trade = new Trade(
                        null,
                        bestBid.getOrderId(),
                        incomingOrder.getOrderId(),
                        incomingOrder.getSymbol(),
                        bestBid.getPrice(),
                        tradeQty,
                        System.currentTimeMillis()
                );

                trades.add(trade);
                incomingOrder.setQuantity(incomingOrder.getQuantity() - tradeQty);
                bestBid.setQuantity(bestBid.getQuantity() - tradeQty);

                if (bestBid.getQuantity() == 0) {
                    orderBook.removeBestBid();
                }
            }
            if (incomingOrder.getQuantity() > 0) {
                orderBook.addAsk(incomingOrder);
            }
        }
        return trades;
    }
}