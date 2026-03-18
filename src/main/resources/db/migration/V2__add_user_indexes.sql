-- V2__add_user_indexes.sql
-- Adds supplemental indexes on the users table for role and active-status queries.
-- V1 already covers: idx_users_email
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE INDEX idx_users_role   ON users(role);
CREATE INDEX idx_users_active ON users(is_active);
