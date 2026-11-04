package com.demo.upimesh.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrustedDeviceSchemaMigrationTest {
    @Test void replacesLegacyOwnerOnlyConstraintWithOwnerAndDeviceConstraint() throws Exception {
        JdbcDataSource dataSource=new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:trusted_device_migration;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(var connection=dataSource.getConnection();Statement statement=connection.createStatement()) {
            statement.execute("CREATE TABLE trusted_devices (owner_user_id VARCHAR(36) NOT NULL, device_id VARCHAR(36) NOT NULL)");
            statement.execute("CREATE UNIQUE INDEX uk_trusted_device_owner_id ON trusted_devices (owner_user_id)");
        }

        new TrustedDeviceSchemaMigration(dataSource).run(null);

        String userId=UUID.randomUUID().toString();
        try(var connection=dataSource.getConnection();Statement statement=connection.createStatement()) {
            statement.execute("INSERT INTO trusted_devices VALUES ('"+userId+"','device-1')");
            assertDoesNotThrow(()->statement.execute("INSERT INTO trusted_devices VALUES ('"+userId+"','device-2')"));
            assertThrows(SQLException.class,()->statement.execute("INSERT INTO trusted_devices VALUES ('"+userId+"','device-1')"));
        }
    }
}
