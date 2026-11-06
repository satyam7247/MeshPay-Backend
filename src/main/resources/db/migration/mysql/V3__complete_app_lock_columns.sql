-- Ensure App Lock fields exist on schemas created by older Hibernate versions.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'app_lock_enabled') = 0, 'ALTER TABLE app_users ADD COLUMN app_lock_enabled BOOLEAN DEFAULT FALSE NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'app_lock_type') = 0, 'ALTER TABLE app_users ADD COLUMN app_lock_type VARCHAR(12)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'app_lock_password_hash') = 0, 'ALTER TABLE app_users ADD COLUMN app_lock_password_hash VARCHAR(100)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'app_lock_failed_attempts') = 0, 'ALTER TABLE app_users ADD COLUMN app_lock_failed_attempts INTEGER DEFAULT 0 NOT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'app_lock_locked_until') = 0, 'ALTER TABLE app_users ADD COLUMN app_lock_locked_until DATETIME(6)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
