package com.trading.market_service.instrument;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SQL for the instruments table. The write methods must run inside ONE transaction: Postgres now() is the
 * transaction's start time, which is how rows touched by this import are told apart from rows left behind.
 */
@Repository
public class InstrumentRepository {

    private static final String UPSERT = """
            INSERT INTO instruments
                (exchange, isin, symbol, name, series, listing_date, face_value, market_lot, industry, nifty500)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (exchange, isin) DO UPDATE SET
                symbol = EXCLUDED.symbol,
                name = EXCLUDED.name,
                series = EXCLUDED.series,
                listing_date = EXCLUDED.listing_date,
                face_value = EXCLUDED.face_value,
                market_lot = EXCLUDED.market_lot,
                industry = EXCLUDED.industry,
                nifty500 = EXCLUDED.nifty500,
                active = TRUE,
                updated_at = now()
            """;

    private final JdbcTemplate jdbc;

    public InstrumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts new stocks and refreshes known ones, sent to the database in batches. */
    public void upsertAll(String exchange, List<ListedInstrument> instruments) {
        jdbc.batchUpdate(UPSERT, instruments, 500, (ps, i) -> {
            ps.setString(1, exchange);
            ps.setString(2, i.isin());
            ps.setString(3, i.symbol());
            ps.setString(4, i.name());
            ps.setString(5, i.series());
            ps.setObject(6, i.listingDate(), Types.DATE);
            ps.setBigDecimal(7, i.faceValue());
            ps.setObject(8, i.marketLot(), Types.INTEGER);
            ps.setString(9, i.industry());
            ps.setBoolean(10, i.nifty500());
        });
    }

    /** Soft-deletes stocks the current import did not touch. Returns how many were switched off. */
    public int deactivateNotUpdated(String exchange) {
        return jdbc.update("""
                UPDATE instruments SET active = FALSE
                WHERE exchange = ? AND active AND updated_at < now()
                """, exchange);
    }

    public int count(String exchange) {
        return jdbc.queryForObject("SELECT count(*) FROM instruments WHERE exchange = ?", Integer.class, exchange);
    }

    /** When the last successful import wrote this exchange; empty if it never did. */
    public Optional<OffsetDateTime> lastUpdated(String exchange) {
        return Optional.ofNullable(jdbc.queryForObject(
                "SELECT max(updated_at) FROM instruments WHERE exchange = ?", OffsetDateTime.class, exchange));
    }
}
