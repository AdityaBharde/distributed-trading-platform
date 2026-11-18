#!/bin/bash
# Runs once, on first start of an empty data volume. Passwords come from the container env (.env file).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
-- Keycloak gets its own role and database.
CREATE ROLE keycloak LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
CREATE DATABASE keycloak OWNER keycloak;

-- Order/matching data (trading-service).
CREATE DATABASE orderdb;

-- Platform database (POSTGRES_DB) already exists: enable time-series, vector and fuzzy-search extensions.
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SQL
