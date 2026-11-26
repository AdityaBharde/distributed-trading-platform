-- One row per issued refresh token. Rotation: each use marks the row used and inserts the next one.
-- All rows of one login share session_id, so "revoke the session" is a single UPDATE.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,                                   -- the JWT "jti"
    session_id  UUID        NOT NULL,                               -- the JWT "sid": one login = one session
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,                               -- hex SHA-256 of the token; never the token itself
    expires_at  TIMESTAMPTZ NOT NULL,                               -- same for every token of a session (absolute cap)
    used_at     TIMESTAMPTZ,                                        -- set when rotated; presenting it again = reuse
    revoked_at  TIMESTAMPTZ,                                        -- set on logout / reuse detection
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
