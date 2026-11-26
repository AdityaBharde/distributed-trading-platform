package com.trading.market_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the Flyway migration on a real Postgres. @Transactional rolls each test's rows back. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InstrumentSchemaTest {

    @Autowired JdbcClient jdbc;

    private void insert(String exchange, String isin, String symbol) {
        jdbc.sql("INSERT INTO instruments (exchange, isin, symbol, name, series) VALUES (?, ?, ?, ?, 'EQ')")
                .params(exchange, isin, symbol, symbol + " Limited")
                .update();
    }

    @Test
    void newRowsAreActiveAndOutsideNifty500ByDefault() {
        insert("NSE", "INE002A01018", "RELIANCE");

        assertThat(jdbc.sql("SELECT active AND NOT nifty500 FROM instruments").query(Boolean.class).single()).isTrue();
    }

    @Test
    void oneRowPerExchangeAndIsin() {
        insert("NSE", "INE002A01018", "RELIANCE");
        insert("BSE", "INE002A01018", "RELIANCE");   // same company, other exchange: allowed

        assertThatThrownBy(() -> insert("NSE", "INE002A01018", "OTHER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownExchangeIsRejected() {
        assertThatThrownBy(() -> insert("XYZ", "INE002A01018", "RELIANCE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void trigramSimilarityIsAvailable() {
        Double score = jdbc.sql("SELECT similarity('reliance', 'relaince')").query(Double.class).single();

        assertThat(score).isGreaterThan(0.3);
    }
}
