package com.trading.market_service.service;

import com.trading.market_service.exception.ImportFailedException;
import com.trading.market_service.instrument.ListedInstrument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NseCsvParserTest {

    // Same quirks as the real file: spaces before header names, upper-case months
    private static final String HEADER =
            "SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE\n";
    private static final String RELIANCE =
            "RELIANCE,Reliance Industries Limited,EQ,29-NOV-1995,10,1,INE002A01018,10\n";
    private static final String NIFTY =
            "Company Name,Industry,Symbol,Series,ISIN Code\n"
            + "Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018\n";

    private final NseCsvParser parser = new NseCsvParser(1, 1);

    @Test
    void readsBothFilesAndJoinsThemByIsin() {
        String quoted = "ABC,\"Abc, Sons and Co Limited\",BE,not-a-date,10,1,INE000A01010,\n";

        List<ListedInstrument> result = parser.parse("﻿" + HEADER + RELIANCE + quoted, NIFTY);

        assertThat(result).containsExactly(
                new ListedInstrument("INE002A01018", "RELIANCE", "Reliance Industries Limited", "EQ",
                        LocalDate.of(1995, 11, 29), new BigDecimal("10"), 1, "Oil Gas & Consumable Fuels", true),
                new ListedInstrument("INE000A01010", "ABC", "Abc, Sons and Co Limited", "BE",
                        null, null, 1, null, false));
    }

    @Test
    void rejectsFilesThatDoNotLookRight() {
        assertRejected("<html>Access denied</html>\n", "no column");
        assertRejected(HEADER, "expected at least");
        assertRejected(HEADER + RELIANCE + "TCS,Tata Consult", "incomplete rows");   // cut off download
        assertRejected(HEADER + RELIANCE + RELIANCE, "repeated ISIN");
        assertRejected(HEADER + "RELIANCE,Reliance,EQ,29-NOV-1995,10,1,SHORT,10\n", "ISIN");
    }

    private void assertRejected(String equityCsv, String reason) {
        assertThatThrownBy(() -> parser.parse(equityCsv, NIFTY))
                .isInstanceOf(ImportFailedException.class)
                .hasMessageContaining(reason);
    }
}
