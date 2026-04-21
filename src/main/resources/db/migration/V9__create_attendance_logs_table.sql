-- V9__create_attendance_logs_table.sql
-- Creates the attendance_logs table for Epic 4 — Attendance & Assistance Tracking.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE attendance_logs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id      UUID         NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    resident_id      UUID         NOT NULL REFERENCES residents(id) ON DELETE CASCADE,
    status           VARCHAR(20)  NOT NULL,
    assistance_level VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    assistance_notes TEXT,
    logged_by        UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    logged_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- One log per resident per activity; use UPDATE to correct a log
    CONSTRAINT uq_attendance_activity_resident UNIQUE (activity_id, resident_id)
);

-- Fast lookup of all attendance records for a resident (history view)
CREATE INDEX idx_attendance_resident ON attendance_logs(resident_id);

-- Fast lookup of all attendance records for an activity (summary view)
CREATE INDEX idx_attendance_activity ON attendance_logs(activity_id);

-- Fast range queries for participation-rate calculations
CREATE INDEX idx_attendance_logged_at ON attendance_logs(logged_at);
