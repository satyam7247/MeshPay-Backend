package com.demo.upimesh.config;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationTest {
    @Test void flywayLocationIsSelectedFromTheDatasourceVendor() throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        assertEquals("classpath:db/migration/{vendor}", properties.getProperty("spring.flyway.locations"));
        assertTrue(Files.exists(Path.of("src/main/resources/db/migration/h2/V1__legacy_ledger.sql")));
        assertTrue(Files.exists(Path.of("src/main/resources/db/migration/mysql/V1__legacy_ledger.sql")));
    }

    @Test void springBootResolvesVendorLocationForAnH2Datasource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:flyway_vendor_location_test;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.flyway.locations=classpath:db/migration/{vendor}")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(Flyway.class));
                    assertEquals(4, context.getBean(Flyway.class).info().all().length);
                });
    }

    @Test void h2MigrationsCreateApplicationSchemaAndStorePurposeAsVarchar() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_migration_test;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/h2").load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            for (String table : new String[]{"accounts", "transactions", "linked_bank_accounts", "bank_otp_challenges",
                    "password_challenges", "app_sessions", "trusted_devices", "notification_push_tokens",
                    "user_notifications", "account_deletion_requests", "account_deletion_otp_challenges"}) {
                try (ResultSet rs = connection.getMetaData().getTables(null, null, table.toUpperCase(), new String[]{"TABLE"})) {
                    assertTrue(rs.next(), "missing table " + table);
                }
            }
            assertColumn(connection, "linked_bank_accounts", "account_fingerprint");
            assertColumn(connection, "linked_bank_accounts", "verify_attempts");
            assertColumn(connection, "bank_otp_challenges", "send_count");
            assertColumn(connection, "bank_otp_challenges", "verified_at");
            assertColumn(connection, "app_users", "app_lock_enabled");
            assertColumn(connection, "app_users", "hide_transaction_details");
            assertColumn(connection, "app_sessions", "app_lock_verified_at");
            assertColumn(connection, "app_sessions", "device_id");
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "PASSWORD_CHALLENGES", "PURPOSE")) {
                assertTrue(rs.next());
                assertEquals("CHARACTER VARYING", rs.getString("TYPE_NAME"));
            }
        }
    }

    @Test void baselineMigrationAddsNewFieldsToAnExistingSchema() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_baseline_test;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE accounts (vpa VARCHAR(255) PRIMARY KEY, holder_name VARCHAR(255), balance DECIMAL(19,2), pin_hash VARCHAR(64), version BIGINT)");
            statement.execute("CREATE TABLE transactions (id BIGINT PRIMARY KEY, packet_hash VARCHAR(64), sender_vpa VARCHAR(255), receiver_vpa VARCHAR(255), amount DECIMAL(19,2), signed_at TIMESTAMP WITH TIME ZONE, settled_at TIMESTAMP WITH TIME ZONE, bridge_node_id VARCHAR(255), hop_count INTEGER, status VARCHAR(255))");
            statement.execute("CREATE TABLE app_users (id UUID PRIMARY KEY, name VARCHAR(80), email VARCHAR(254), phone VARCHAR(18), password_hash VARCHAR(100), upi_id VARCHAR(80), created_at TIMESTAMP WITH TIME ZONE)");
            statement.execute("CREATE TABLE app_sessions (token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36), expires_at TIMESTAMP WITH TIME ZONE)");
            statement.execute("CREATE TABLE linked_bank_accounts (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE bank_otp_challenges (id UUID PRIMARY KEY)");
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/h2")
                .baselineOnMigrate(true).baselineVersion("1").load().migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertColumn(connection, "app_users", "app_lock_enabled");
            assertColumn(connection, "app_users", "recipient_discoverable");
            assertColumn(connection, "app_sessions", "app_lock_verified_at");
            assertColumn(connection, "app_sessions", "device_id");
            assertColumn(connection, "linked_bank_accounts", "account_fingerprint");
            assertColumn(connection, "bank_otp_challenges", "send_count");
        }
    }

    private void assertColumn(Connection connection, String table, String column) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            assertTrue(rs.next(), "missing column " + table + "." + column);
        }
    }
}
