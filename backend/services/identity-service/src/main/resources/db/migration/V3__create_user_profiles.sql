-- Preferences, one row per user. Kept apart from "users": identity data and preferences change for different reasons.
CREATE TABLE user_profiles (
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    theme            VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    default_exchange VARCHAR(10)  NOT NULL DEFAULT 'NSE',
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_profiles_theme CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT ck_profiles_exchange CHECK (default_exchange IN ('NSE', 'BSE', 'NASDAQ', 'NYSE'))
);
