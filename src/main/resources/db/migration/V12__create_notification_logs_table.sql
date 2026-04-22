-- V12__create_notification_logs_table.sql
-- Creates the notification_logs table for Epic 5 — Notifications & Reminders.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE notification_logs (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(30) NOT NULL,
    channel           VARCHAR(10) NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'RETRYING',
    reference_id      UUID,
    message           TEXT,
    error_message     TEXT,
    attempt_count     INT         NOT NULL DEFAULT 0,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fast lookup of all logs for a given user
CREATE INDEX idx_notif_log_user      ON notification_logs(user_id);

-- Used by retry job to find FAILED/RETRYING logs efficiently
CREATE INDEX idx_notif_log_status    ON notification_logs(status);

-- Used to find all logs associated with a specific activity or enrollment
CREATE INDEX idx_notif_log_reference ON notification_logs(reference_id);

-- Used for time-range queries on delivery history
CREATE INDEX idx_notif_log_sent_at   ON notification_logs(sent_at);
