package com.trading.market_service.exception;

public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(String symbol) {
        super("No stock with symbol " + symbol);
    }
}
