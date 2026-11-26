#!/bin/bash
# Runs once, on first start of an empty data volume. Passwords come from the container env (.env file).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
-- Order/matching data (trading-service).
CREATE DATABASE orderdb;

-- User data owned by analytics-service (watchlists, drawings, alerts later).
CREATE DATABASE analyticsdb;

-- Accounts, refresh tokens and profiles (identity-service). Schema managed by Flyway.
CREATE DATABASE identitydb;

-- Instrument master, later candles (market-service). Schema managed by Flyway.
CREATE DATABASE marketdb;

-- Platform database (POSTGRES_DB) already exists: enable time-series, vector and fuzzy-search extensions.
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SQL
