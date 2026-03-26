-- V7__create_activity_table.sql
-- Creates the activities table for Epic 3 — Activity Scheduling.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

CREATE TABLE activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    category        VARCHAR(50)     NOT NULL,
    location        VARCHAR(255)    NOT NULL,
    start_time      TIMESTAMPTZ     NOT NULL,
    end_time        TIMESTAMPTZ     NOT NULL,
    capacity        INTEGER         NOT NULL CHECK (capacity > 0),
    recurrence_rule VARCHAR(500),
    status          VARCHAR(50)     NOT NULL DEFAULT 'SCHEDULED',
    created_by      UUID            REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Index for calendar range queries (most frequent access pattern)
CREATE INDEX idx_activities_start_time ON activities(start_time)
    WHERE deleted_at IS NULL;

-- Index for category filter on calendar / list views
CREATE INDEX idx_activities_category ON activities(category)
    WHERE deleted_at IS NULL;

-- Index for status filter (e.g. show only SCHEDULED activities)
CREATE INDEX idx_activities_status ON activities(status)
    WHERE deleted_at IS NULL;
