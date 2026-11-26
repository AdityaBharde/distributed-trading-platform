package com.trading.market_service.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One stock as the exchange files describe it today. The database adds id, active and timestamps. */
public record ListedInstrument(
        String isin,
        String symbol,
        String name,
        String series,
        LocalDate listingDate,   // null if the file had no usable value (also faceValue, marketLot)
        BigDecimal faceValue,
        Integer marketLot,
        String industry,         // null unless the stock is in the NIFTY 500 file
        boolean nifty500) {
}
