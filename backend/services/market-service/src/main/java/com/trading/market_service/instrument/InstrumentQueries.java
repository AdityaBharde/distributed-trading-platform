package com.trading.market_service.instrument;

import com.trading.market_service.dto.InstrumentResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Read side of the instruments table (the import's writes are in InstrumentRepository). */
@Repository
public class InstrumentQueries {

    private static final String COLUMNS = """
            exchange, symbol, name, series, isin, industry, nifty500,
            listing_date, face_value, market_lot, active
            """;

    /*
     * tier 0 = exact symbol, 1 = symbol starts with, 2 = name starts with, 3 = looks similar (typos).
     * Inside a tier: NIFTY 500 first, then the closer match, then alphabetical.
     */
    private static final String SEARCH = """
            SELECT %s
            FROM (
                SELECT *,
                       CASE WHEN lower(symbol) = :q THEN 0
                            WHEN lower(symbol) LIKE :prefix THEN 1
                            WHEN lower(name) LIKE :prefix THEN 2
                            ELSE 3 END AS tier,
                       GREATEST(similarity(lower(symbol), :q), word_similarity(:q, lower(name))) AS score
                FROM instruments
                WHERE exchange = 'NSE' AND active
            ) ranked
            WHERE tier < 3 OR (:fuzzy AND score >= 0.35)
            ORDER BY tier, nifty500 DESC, CASE WHEN tier = 3 THEN score END DESC, symbol
            LIMIT :limit
            """.formatted(COLUMNS);

    private static final int MIN_FUZZY_LENGTH = 3;   // one or two letters are "similar" to far too much

    private final JdbcClient jdbc;

    public InstrumentQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<InstrumentResponse> search(String query, int limit) {
        String q = query.toLowerCase(Locale.ROOT);
        return jdbc.sql(SEARCH)
                .param("q", q)
                .param("prefix", escapeLike(q) + "%")
                .param("fuzzy", q.length() >= MIN_FUZZY_LENGTH)
                .param("limit", limit)
                .query(InstrumentResponse.class)   // maps listing_date -> listingDate and so on
                .list();
    }

    /** Delisted stocks are returned too (marked inactive), so old links keep working. */
    public Optional<InstrumentResponse> findBySymbol(String symbol) {
        return jdbc.sql("""
                        SELECT %s FROM instruments
                        WHERE exchange = 'NSE' AND symbol = :symbol
                        ORDER BY active DESC, updated_at DESC
                        LIMIT 1
                        """.formatted(COLUMNS))
                .param("symbol", symbol.toUpperCase(Locale.ROOT))
                .query(InstrumentResponse.class)
                .optional();
    }

    /** The user's % and _ must match themselves, not act as LIKE wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
