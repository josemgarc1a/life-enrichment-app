-- V6__alter_resident_family_members.sql
-- Rename relationship → relationship_label; add linked_at audit column.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

-- ============================================================
-- STEP 1: Rename relationship → relationship_label
-- ============================================================
ALTER TABLE resident_family_members
    RENAME COLUMN relationship TO relationship_label;

-- ============================================================
-- STEP 2: Add linked_at with a default so existing rows get a value
-- ============================================================
ALTER TABLE resident_family_members
    ADD COLUMN linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
