-- EventFlow schema additions for MySQL 8 (AWS RDS).
--
-- You do NOT need this file if you run with SPRING_PROFILES_ACTIVE=prod and DDL_AUTO=update (the default):
-- Hibernate then creates exactly these tables/columns for you and never drops or alters existing data.
-- Use it only if you prefer to apply schema changes yourself (run with DDL_AUTO=validate afterwards).
--
-- The original project used an in-memory H2 database, so there is no older production data to convert.
-- All changes are additive (new tables, new nullable columns).

-- 1. New columns on the existing events table (all nullable, existing rows stay valid)
ALTER TABLE events
    ADD COLUMN capacity      INT          NULL,
    ADD COLUMN organizer_id  BIGINT       NULL,
    ADD COLUMN poster_key    VARCHAR(300) NULL;

-- 2. Login accounts
CREATE TABLE IF NOT EXISTS app_users (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(190) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email)
);

ALTER TABLE events
    ADD CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES app_users (id);

-- 3. Registrations (also holds ticket id, waitlist order and check-in time)
CREATE TABLE IF NOT EXISTS registrations (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id       BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    ticket_id      VARCHAR(40)  NULL,
    registered_at  DATETIME(6)  NOT NULL,
    queued_at      DATETIME(6)  NOT NULL,
    cancelled_at   DATETIME(6)  NULL,
    checked_in_at  DATETIME(6)  NULL,
    CONSTRAINT uk_registration_event_user UNIQUE (event_id, user_id),
    CONSTRAINT uk_registration_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_registration_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_registration_user  FOREIGN KEY (user_id)  REFERENCES app_users (id)
);

-- 4. Certificates (one per registration)
CREATE TABLE IF NOT EXISTS certificates (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    registration_id   BIGINT       NOT NULL,
    certificate_code  VARCHAR(40)  NOT NULL,
    participant_name  VARCHAR(120) NOT NULL,
    event_name        VARCHAR(200) NOT NULL,
    event_date        DATE         NOT NULL,
    organizer_name    VARCHAR(200) NULL,
    issued_at         DATETIME(6)  NOT NULL,
    storage_key       VARCHAR(300) NULL,
    CONSTRAINT uk_certificate_registration UNIQUE (registration_id),
    CONSTRAINT uk_certificate_code UNIQUE (certificate_code),
    CONSTRAINT fk_certificate_registration FOREIGN KEY (registration_id) REFERENCES registrations (id)
);
