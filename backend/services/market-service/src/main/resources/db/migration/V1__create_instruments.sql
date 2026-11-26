-- Instrument master: one row per listed stock. Filled by the importer (F2 step 2).

-- Trigram matching for fuzzy search ("relaince" still finds RELIANCE)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE instruments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- stable id that later features reference
    exchange     VARCHAR(8)   NOT NULL,
    isin         VARCHAR(12)  NOT NULL,
    symbol       VARCHAR(32)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    series       VARCHAR(4)   NOT NULL,          -- NSE series: EQ, BE, BZ
    listing_date DATE,
    face_value   NUMERIC(10, 2),
    market_lot   INTEGER,
    industry     VARCHAR(100),                   -- only known for NIFTY 500 members
    nifty500     BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,  -- FALSE when it disappears from the exchange list
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(), -- set by every import; tells how old the data is
    CONSTRAINT instruments_exchange_check CHECK (exchange IN ('NSE', 'BSE')),
    CONSTRAINT instruments_isin_check CHECK (length(isin) = 12),
    -- The natural key the import upserts on: a symbol can be renamed, the ISIN usually survives it
    CONSTRAINT instruments_exchange_isin_key UNIQUE (exchange, isin)
);

-- Stock page lookup. Not unique: renames or swapped symbols must not make an import fail half way.
CREATE INDEX instruments_exchange_symbol_idx ON instruments (exchange, symbol);

-- Trigram indexes on lower(...) so search is case-insensitive
CREATE INDEX instruments_symbol_trgm_idx ON instruments USING gin (lower(symbol) gin_trgm_ops);
CREATE INDEX instruments_name_trgm_idx ON instruments USING gin (lower(name) gin_trgm_ops);
