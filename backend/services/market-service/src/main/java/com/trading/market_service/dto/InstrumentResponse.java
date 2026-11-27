package com.trading.market_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stock as the API returns it. The internal id and timestamps stay inside the service. */
public record InstrumentResponse(
        String exchange,
        String symbol,
        String name,
        String series,
        String isin,
        String industry,         // null outside the NIFTY 500
        boolean nifty500,
        LocalDate listingDate,
        BigDecimal faceValue,
        Integer marketLot,
        boolean active) {        // false = no longer on the exchange list
}
