-- Apply this migration before deploying the App Lock backend with the prod profile.
ALTER TABLE app_users
    ADD COLUMN app_lock_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN app_lock_type VARCHAR(12) NULL,
    ADD COLUMN app_lock_password_hash VARCHAR(100) NULL,
    ADD COLUMN app_lock_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN app_lock_locked_until DATETIME(6) NULL;

ALTER TABLE app_sessions
    ADD COLUMN app_lock_verified BOOLEAN NOT NULL DEFAULT FALSE;
