-- Tables created on fresh installs use their complete current shape.
-- Baseline installs skip V1 and conditionally add columns Hibernate may not have created.
CREATE TABLE IF NOT EXISTS app_users (
  id BINARY(16) PRIMARY KEY, name VARCHAR(80) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE,
  phone VARCHAR(18) NOT NULL UNIQUE, phone_normalized VARCHAR(18), password_hash VARCHAR(100) NOT NULL,
  upi_id VARCHAR(80) NOT NULL UNIQUE, recipient_discoverable BOOLEAN DEFAULT FALSE NOT NULL,
  recipient_version INTEGER DEFAULT 1 NOT NULL, recipient_updated_at DATETIME(6), created_at DATETIME(6) NOT NULL,
  offline_payment_limit DECIMAL(12,2) DEFAULT 0.00 NOT NULL, hide_balance BOOLEAN DEFAULT FALSE NOT NULL,
  hide_transaction_details BOOLEAN DEFAULT FALSE NOT NULL, account_locked BOOLEAN DEFAULT FALSE NOT NULL,
  account_locked_at DATETIME(6), app_lock_enabled BOOLEAN DEFAULT FALSE NOT NULL, app_lock_type VARCHAR(12),
  app_lock_password_hash VARCHAR(100), app_lock_failed_attempts INTEGER DEFAULT 0 NOT NULL,
  app_lock_locked_until DATETIME(6)
);
CREATE TABLE IF NOT EXISTS app_sessions (
  token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, expires_at DATETIME(6) NOT NULL,
  device_id VARCHAR(64), app_lock_verified BOOLEAN DEFAULT FALSE NOT NULL, app_lock_verified_at DATETIME(6)
);
CREATE TABLE IF NOT EXISTS linked_bank_accounts (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, bank_name VARCHAR(100) NOT NULL,
  account_holder_name VARCHAR(512) NOT NULL, masked_account_number VARCHAR(20) NOT NULL,
  account_fingerprint VARCHAR(64), verify_attempts INTEGER DEFAULT 0 NOT NULL,
  verification_status VARCHAR(16) NOT NULL, verification_mode VARCHAR(24) NOT NULL,
  verification_message VARCHAR(300), provider_reference VARCHAR(100),
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_linked_bank_owner_fingerprint UNIQUE(owner_user_id, account_fingerprint)
);
CREATE TABLE IF NOT EXISTS bank_otp_challenges (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL, resend_after DATETIME(6) NOT NULL, attempts INTEGER NOT NULL,
  otp_hash VARCHAR(64), send_count INTEGER DEFAULT 1 NOT NULL, verified BOOLEAN NOT NULL,
  verified_at DATETIME(6), consumed BOOLEAN NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS password_challenges (
  id BINARY(16) PRIMARY KEY, user_id VARCHAR(36), email_key VARCHAR(64) NOT NULL,
  requester_key VARCHAR(64), purpose VARCHAR(12) NOT NULL, otp_hash VARCHAR(100),
  otp_expires_at DATETIME(6), reset_token_hash VARCHAR(64), reset_expires_at DATETIME(6),
  attempts INTEGER NOT NULL, otp_consumed BOOLEAN NOT NULL, reset_consumed BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS trusted_devices (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, device_id VARCHAR(36) NOT NULL,
  device_name VARCHAR(80) NOT NULL, platform VARCHAR(16) NOT NULL, verification_method VARCHAR(32) NOT NULL,
  registered_at DATETIME(6) NOT NULL, last_active_at DATETIME(6) NOT NULL, revoked_at DATETIME(6),
  CONSTRAINT uk_trusted_device_owner_id UNIQUE(owner_user_id, device_id)
);
CREATE TABLE IF NOT EXISTS notification_push_tokens (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, expo_push_token VARCHAR(255) NOT NULL UNIQUE,
  enabled BOOLEAN NOT NULL, sound_enabled BOOLEAN NOT NULL, updated_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS user_notifications (
  id BINARY(16) PRIMARY KEY, recipient_user_id BINARY(16) NOT NULL, transaction_id BIGINT NOT NULL UNIQUE,
  sender_name VARCHAR(80) NOT NULL, amount DECIMAL(19,2) NOT NULL, payment_status VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL, read_at DATETIME(6)
);
CREATE TABLE IF NOT EXISTS account_deletion_requests (
  id BINARY(16) PRIMARY KEY, user_id BINARY(16) NOT NULL, request_created_at DATETIME(6) NOT NULL,
  scheduled_deletion_at DATETIME(6) NOT NULL, status VARCHAR(24) NOT NULL, cancelled_at DATETIME(6),
  deleted_at DATETIME(6), prior_account_locked BOOLEAN NOT NULL, deleted_email_key VARCHAR(64)
);
CREATE TABLE IF NOT EXISTS account_deletion_otp_challenges (
  id BINARY(16) PRIMARY KEY, user_id BINARY(16) NOT NULL, created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL, resend_after DATETIME(6) NOT NULL, attempts INTEGER NOT NULL,
  consumed BOOLEAN NOT NULL, verified BOOLEAN NOT NULL, otp_hash VARCHAR(64), version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS offline_transaction_logs (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, customer_upi_id VARCHAR(80) NOT NULL,
  merchant_upi_id VARCHAR(255) NOT NULL, amount DECIMAL(19,2) NOT NULL, local_timestamp DATETIME(6) NOT NULL,
  bluetooth_txn_id VARCHAR(128) NOT NULL UNIQUE, sync_status VARCHAR(16) NOT NULL, received_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS saved_recipients (
  id BINARY(16) PRIMARY KEY, owner_user_id BINARY(16) NOT NULL, recipient_user_id BINARY(16) NOT NULL,
  saved_at DATETIME(6) NOT NULL, CONSTRAINT uk_saved_recipient_owner_target UNIQUE(owner_user_id, recipient_user_id)
);

-- This conditional ALTER form is supported by stock MySQL 8.0 and safely handles Hibernate-created schemas.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'pin_failed_attempts') = 0, 'ALTER TABLE accounts ADD COLUMN pin_failed_attempts INTEGER DEFAULT 0 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'pin_locked_until') = 0, 'ALTER TABLE accounts ADD COLUMN pin_locked_until DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'transaction_nonce') = 0, 'ALTER TABLE transactions ADD COLUMN transaction_nonce VARCHAR(64) UNIQUE', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'rejection_reason') = 0, 'ALTER TABLE transactions ADD COLUMN rejection_reason VARCHAR(64)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_sessions' AND column_name = 'device_id') = 0, 'ALTER TABLE app_sessions ADD COLUMN device_id VARCHAR(64)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_sessions' AND column_name = 'app_lock_verified') = 0, 'ALTER TABLE app_sessions ADD COLUMN app_lock_verified BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_sessions' AND column_name = 'app_lock_verified_at') = 0, 'ALTER TABLE app_sessions ADD COLUMN app_lock_verified_at DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'linked_bank_accounts' AND column_name = 'account_fingerprint') = 0, 'ALTER TABLE linked_bank_accounts ADD COLUMN account_fingerprint VARCHAR(64)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'linked_bank_accounts' AND column_name = 'verify_attempts') = 0, 'ALTER TABLE linked_bank_accounts ADD COLUMN verify_attempts INTEGER DEFAULT 0 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bank_otp_challenges' AND column_name = 'send_count') = 0, 'ALTER TABLE bank_otp_challenges ADD COLUMN send_count INTEGER DEFAULT 1 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bank_otp_challenges' AND column_name = 'verified_at') = 0, 'ALTER TABLE bank_otp_challenges ADD COLUMN verified_at DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

ALTER TABLE password_challenges MODIFY COLUMN purpose VARCHAR(12) NOT NULL;
