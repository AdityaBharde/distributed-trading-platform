package com.trading.market_service.exception;

public class ImportInProgressException extends RuntimeException {

    public ImportInProgressException() {
        super("An import is already running");
    }
}
