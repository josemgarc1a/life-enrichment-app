-- V3__add_refresh_token_to_users.sql
-- Adds refresh_token column to users table for server-side token invalidation on logout.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

ALTER TABLE users ADD COLUMN refresh_token VARCHAR(512);
