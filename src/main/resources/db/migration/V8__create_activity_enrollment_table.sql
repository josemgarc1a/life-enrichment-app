-- V8__create_activity_enrollment_table.sql
-- Creates the activity_enrollments join table for Epic 3 — Activity Scheduling.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE activity_enrollments (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id  UUID        NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    resident_id  UUID        NOT NULL REFERENCES residents(id) ON DELETE CASCADE,
    enrolled_by  UUID        REFERENCES users(id) ON DELETE SET NULL,
    enrolled_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Prevent a resident from being enrolled in the same activity twice
    CONSTRAINT uq_activity_resident UNIQUE (activity_id, resident_id)
);

-- Index for fast roster lookup (all residents in a given activity)
CREATE INDEX idx_enrollments_activity_id ON activity_enrollments(activity_id);

-- Index for fast personal schedule lookup (all activities a resident is enrolled in)
CREATE INDEX idx_enrollments_resident_id ON activity_enrollments(resident_id);
