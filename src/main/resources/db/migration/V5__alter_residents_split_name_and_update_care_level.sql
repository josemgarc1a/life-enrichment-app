-- V5__alter_residents_split_name_and_update_care_level.sql
-- Split full_name into first_name + last_name; rename care level MODERATE → MEDIUM.
-- Managed by Flyway. Do NOT modify; create a new migration instead.

-- ============================================================
-- STEP 1: Add first_name and last_name columns
-- ============================================================
ALTER TABLE residents
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name  VARCHAR(100);

-- ============================================================
-- STEP 2: Populate from full_name (split on first space)
-- ============================================================
UPDATE residents
SET first_name = SPLIT_PART(full_name, ' ', 1),
    last_name  = CASE
                     WHEN POSITION(' ' IN full_name) > 0
                         THEN SUBSTRING(full_name FROM POSITION(' ' IN full_name) + 1)
                     ELSE ''
                 END;

-- ============================================================
-- STEP 3: Apply NOT NULL constraints
-- ============================================================
ALTER TABLE residents
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name  SET NOT NULL;

-- ============================================================
-- STEP 4: Drop old full_name column and its index
-- ============================================================
DROP INDEX IF EXISTS idx_residents_name;

ALTER TABLE residents
    DROP COLUMN full_name;

-- ============================================================
-- STEP 5: Rename care level MODERATE → MEDIUM in existing data
-- ============================================================
UPDATE residents
SET care_level = 'MEDIUM'
WHERE care_level = 'MODERATE';

-- ============================================================
-- STEP 6: Replace the care_level check constraint
-- ============================================================
ALTER TABLE residents
    DROP CONSTRAINT IF EXISTS residents_care_level_check;

ALTER TABLE residents
    ADD CONSTRAINT residents_care_level_check
        CHECK (care_level IN ('LOW', 'MEDIUM', 'HIGH'));

-- ============================================================
-- STEP 7: Add indexes on room_number and care_level
-- ============================================================
CREATE INDEX idx_residents_room_number ON residents(room_number);
CREATE INDEX idx_residents_care_level  ON residents(care_level);
