-- Ensure account privacy and recipient fields exist on older Hibernate-created schemas.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'phone_normalized') = 0, 'ALTER TABLE app_users ADD COLUMN phone_normalized VARCHAR(18)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'recipient_discoverable') = 0, 'ALTER TABLE app_users ADD COLUMN recipient_discoverable BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'recipient_version') = 0, 'ALTER TABLE app_users ADD COLUMN recipient_version INTEGER DEFAULT 1 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'recipient_updated_at') = 0, 'ALTER TABLE app_users ADD COLUMN recipient_updated_at DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'offline_payment_limit') = 0, 'ALTER TABLE app_users ADD COLUMN offline_payment_limit DECIMAL(12,2) DEFAULT 0.00 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'hide_balance') = 0, 'ALTER TABLE app_users ADD COLUMN hide_balance BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'hide_transaction_details') = 0, 'ALTER TABLE app_users ADD COLUMN hide_transaction_details BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'account_locked') = 0, 'ALTER TABLE app_users ADD COLUMN account_locked BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'account_locked_at') = 0, 'ALTER TABLE app_users ADD COLUMN account_locked_at DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_sessions' AND column_name = 'device_id') = 0, 'ALTER TABLE app_sessions ADD COLUMN device_id VARCHAR(64)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
