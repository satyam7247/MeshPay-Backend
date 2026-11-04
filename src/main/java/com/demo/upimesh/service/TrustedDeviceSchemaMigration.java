package com.demo.upimesh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Repairs the legacy one-device-per-user unique constraint without changing rows.
 * Hibernate's ddl-auto=update does not replace an existing unique constraint when
 * its column set changes, so this migration runs after Hibernate creates the schema.
 */
@Component
public class TrustedDeviceSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TrustedDeviceSchemaMigration.class);
    private static final String INDEX_NAME = "uk_trusted_device_owner_id";
    private static final Set<String> REQUIRED_COLUMNS = Set.of("owner_user_id", "device_id");

    private final DataSource dataSource;

    public TrustedDeviceSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseTable table = findTrustedDeviceTable(connection);
            if (table == null) return;

            Map<String, List<String>> uniqueIndexes = readUniqueIndexes(connection, table);
            log.info("Trusted-device unique indexes: {}", uniqueIndexes);
            List<String> existingColumns = uniqueIndexes.get(INDEX_NAME);
            boolean compositeUniqueIndexExists = uniqueIndexes.values().stream()
                    .anyMatch(columns -> columns.size() == REQUIRED_COLUMNS.size()
                            && Set.copyOf(columns).equals(REQUIRED_COLUMNS));
            List<String> obsoleteIndexes = uniqueIndexes.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(List.of("owner_user_id"))
                            || (entry.getKey().equals(INDEX_NAME)
                                && (entry.getValue().size() != REQUIRED_COLUMNS.size()
                                    || !Set.copyOf(entry.getValue()).equals(REQUIRED_COLUMNS))))
                    .map(Map.Entry::getKey)
                    .toList();

            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            try (Statement statement = connection.createStatement()) {
                for (String indexName : obsoleteIndexes) {
                    if (product.contains("mysql") || product.contains("mariadb")) {
                        statement.execute("ALTER TABLE trusted_devices DROP INDEX " + indexName);
                    } else if (product.contains("h2")) {
                        statement.execute("DROP INDEX " + indexName);
                    } else {
                        statement.execute("ALTER TABLE trusted_devices DROP CONSTRAINT " + indexName);
                    }
                    log.info("Removed legacy trusted-device unique index {}.", indexName);
                }
                if (!compositeUniqueIndexExists) {
                    statement.execute("ALTER TABLE trusted_devices ADD CONSTRAINT " + INDEX_NAME
                            + " UNIQUE (owner_user_id, device_id)");
                    log.info("Migrated trusted-device uniqueness to owner and device ID.");
                }
            }
        }
    }

    private static DatabaseTable findTrustedDeviceTable(Connection connection) throws Exception {
        var metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, null,
                new String[]{"TABLE"})) {
            while (tables.next()) {
                if ("trusted_devices".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return new DatabaseTable(tables.getString("TABLE_CAT"),
                            tables.getString("TABLE_SCHEM"), tables.getString("TABLE_NAME"));
                }
            }
        }
        return null;
    }

    private static Map<String, List<String>> readUniqueIndexes(Connection connection, DatabaseTable table)
            throws Exception {
        Map<String, TreeMap<Short, String>> columnsByIndex = new HashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), true, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                String column = indexes.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                columnsByIndex.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new TreeMap<>())
                        .put(indexes.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }

        Map<String, List<String>> result = new HashMap<>();
        columnsByIndex.forEach((name, columns) -> result.put(name, new ArrayList<>(columns.values())));
        return result;
    }

    private record DatabaseTable(String catalog, String schema, String name) {}
}
