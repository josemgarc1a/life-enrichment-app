-- V4__password_reset_tokens.sql
-- Creates password_reset_tokens table for the forgot-password / reset-password flow.
-- Token is stored as a SHA-256 hash; the raw UUID is sent only via email.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_prt_user_id    ON password_reset_tokens(user_id);
CREATE INDEX idx_prt_expires_at ON password_reset_tokens(expires_at);
