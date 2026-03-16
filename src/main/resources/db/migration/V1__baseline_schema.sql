-- V1__baseline_schema.sql
-- Life Enrichment App — initial database schema
-- Managed by Flyway. Do NOT modify; create a new migration instead.

-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL CHECK (role IN ('DIRECTOR', 'STAFF', 'FAMILY_MEMBER')),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    fcm_token       VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- RESIDENTS
-- ============================================================
CREATE TABLE residents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(255) NOT NULL,
    date_of_birth   DATE,
    room_number     VARCHAR(20),
    care_level      VARCHAR(20) CHECK (care_level IN ('LOW', 'MODERATE', 'HIGH')),
    preferences     TEXT,
    photo_url       VARCHAR(512),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_residents_name     ON residents(full_name);
CREATE INDEX idx_residents_active   ON residents(is_active);

-- ============================================================
-- RESIDENT <-> FAMILY MEMBER
-- ============================================================
CREATE TABLE resident_family_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resident_id     UUID        NOT NULL REFERENCES residents(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relationship    VARCHAR(50),
    UNIQUE (resident_id, user_id)
);

CREATE INDEX idx_rfm_resident ON resident_family_members(resident_id);
CREATE INDEX idx_rfm_user     ON resident_family_members(user_id);

-- ============================================================
-- ACTIVITIES
-- ============================================================
CREATE TABLE activities (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    category         VARCHAR(50) CHECK (category IN ('FITNESS','ARTS','SOCIAL','COGNITIVE','MUSIC','OUTDOOR','OTHER')),
    location         VARCHAR(255),
    start_time       TIMESTAMPTZ  NOT NULL,
    end_time         TIMESTAMPTZ  NOT NULL,
    capacity         INTEGER,
    recurrence_rule  VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED','CANCELLED','COMPLETED')),
    created_by       UUID         REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activities_start   ON activities(start_time);
CREATE INDEX idx_activities_status  ON activities(status);
CREATE INDEX idx_activities_category ON activities(category);

-- ============================================================
-- ACTIVITY ENROLLMENTS
-- ============================================================
CREATE TABLE activity_enrollments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id  UUID        NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    resident_id  UUID        NOT NULL REFERENCES residents(id)  ON DELETE CASCADE,
    enrolled_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (activity_id, resident_id)
);

CREATE INDEX idx_enrollment_activity ON activity_enrollments(activity_id);
CREATE INDEX idx_enrollment_resident ON activity_enrollments(resident_id);

-- ============================================================
-- ATTENDANCE LOGS
-- ============================================================
CREATE TABLE attendance_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id      UUID        NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    resident_id      UUID        NOT NULL REFERENCES residents(id)  ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('ATTENDED','ABSENT','DECLINED')),
    assistance_level VARCHAR(20) NOT NULL DEFAULT 'NONE'
                        CHECK (assistance_level IN ('NONE','MINIMAL','MODERATE','FULL')),
    assistance_notes TEXT,
    logged_by        UUID        NOT NULL REFERENCES users(id),
    logged_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attendance_resident  ON attendance_logs(resident_id);
CREATE INDEX idx_attendance_activity  ON attendance_logs(activity_id);
CREATE INDEX idx_attendance_logged_at ON attendance_logs(logged_at);

-- ============================================================
-- PHOTOS
-- ============================================================
CREATE TABLE photos (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id      UUID        REFERENCES activities(id) ON DELETE SET NULL,
    uploaded_by      UUID        NOT NULL REFERENCES users(id),
    s3_key           VARCHAR(512) NOT NULL,
    thumbnail_s3_key VARCHAR(512),
    caption          VARCHAR(500),
    approval_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
    taken_at         TIMESTAMPTZ,
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_photos_activity        ON photos(activity_id);
CREATE INDEX idx_photos_approval_status ON photos(approval_status);

-- ============================================================
-- PHOTO RESIDENT TAGS
-- ============================================================
CREATE TABLE photo_resident_tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    photo_id    UUID NOT NULL REFERENCES photos(id)    ON DELETE CASCADE,
    resident_id UUID NOT NULL REFERENCES residents(id) ON DELETE CASCADE,
    UNIQUE (photo_id, resident_id)
);

CREATE INDEX idx_prt_photo    ON photo_resident_tags(photo_id);
CREATE INDEX idx_prt_resident ON photo_resident_tags(resident_id);

-- ============================================================
-- NOTIFICATION LOGS
-- ============================================================
CREATE TABLE notification_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(50) NOT NULL,
    channel     VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    status      VARCHAR(20) NOT NULL CHECK (status IN ('SENT','FAILED','PENDING')),
    reference   VARCHAR(255),
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_user    ON notification_logs(user_id);
CREATE INDEX idx_notif_sent_at ON notification_logs(sent_at);

-- ============================================================
-- AUDIT LOGS
-- ============================================================
CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        REFERENCES users(id) ON DELETE SET NULL,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100),
    entity_id    UUID,
    ip_address   VARCHAR(45),
    details      TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user        ON audit_logs(user_id);
CREATE INDEX idx_audit_occurred_at ON audit_logs(occurred_at);
