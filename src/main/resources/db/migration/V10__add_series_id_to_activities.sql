-- V10__add_series_id_to_activities.sql
-- Adds series_id to activities for recurring activity expansion (Epic 3 — Story 3-5).
-- Template rows have series_id = NULL and recurrence_rule set.
-- Generated occurrence rows have series_id = <template.id> and recurrence_rule = NULL.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

ALTER TABLE activities
    ADD COLUMN series_id UUID REFERENCES activities(id) ON DELETE SET NULL;

-- Fast lookup of all occurrences belonging to a series
CREATE INDEX idx_activities_series_id ON activities(series_id);
