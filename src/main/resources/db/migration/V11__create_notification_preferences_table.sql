-- V11__create_notification_preferences_table.sql
-- Creates the notification_preferences table for Epic 5 — Notifications & Reminders.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE notification_preferences (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(30) NOT NULL,
    email_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    push_enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- One preference row per user per notification type
    CONSTRAINT uq_notification_preference_user_type UNIQUE (user_id, notification_type)
);

-- Fast lookup of all preferences for a given user
CREATE INDEX idx_notif_pref_user ON notification_preferences(user_id);
