CREATE TABLE refresh_tokens (
                                id              UUID PRIMARY KEY,
                                user_id         UUID NOT NULL REFERENCES users(id),
                                token_hash      VARCHAR(64) NOT NULL,  -- SHA-256 of token, NOT the token itself!
                                family          UUID NOT NULL,          -- for token rotation detection
                                issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                expires_at      TIMESTAMPTZ NOT NULL,
                                revoked_at      TIMESTAMPTZ,            -- NULL = active
                                revoked_reason  VARCHAR(50),            -- LOGOUT, PASSWORD_CHANGE, SUSPICIOUS_ACTIVITY
                                ip_address      INET,
                                user_agent      VARCHAR(500),

                                UNIQUE(token_hash)
);

-- Index for fast lookup
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;