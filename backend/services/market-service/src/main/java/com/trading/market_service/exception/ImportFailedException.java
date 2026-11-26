package com.trading.market_service.exception;

/** A file could not be downloaded or did not look right. Nothing was written. */
public class ImportFailedException extends RuntimeException {

    public ImportFailedException(String message) {
        super(message);
    }

    public ImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
