package com.bloom.app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V21UpgradePathMigrationTest {

    @Test
    void upgradesV20DataWithoutLosingAuthoritativeHistory() throws Exception {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
        postgres.start();
        try {
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:migration")
                .target("20")
                .load()
                .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    UPDATE items
                    SET stock_quantity = 999.0000,
                        stock_store = 12.5000,
                        stock_warehouse = 3.2500
                    WHERE id = 1
                    """);
                statement.executeUpdate("""
                    INSERT INTO stock_movements (
                        product_id, movement_type, source_type, source_id, quantity,
                        created_at, created_by, stock_location, qty_before, qty_after,
                        reference_no, adjustment_action_type
                    ) VALUES (
                        1, 'IN', 'OPENING_BALANCE', 1, 12.5000,
                        CURRENT_TIMESTAMP, 'upgrade-test', 'STORE', 0.0000, 12.5000,
                        'UPGRADE-OPENING-1', NULL
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO suppliers (
                        name, code, active, created_at, created_by, version
                    ) VALUES (
                        'Supplier current name', 'UPGRADE-SUPPLIER', TRUE,
                        CURRENT_TIMESTAMP, 'upgrade-test', 0
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO goods_receipts (
                        code, received_date, supplier_name, supplier_id, total_amount,
                        created_at, created_by, create_idempotency_key,
                        create_request_hash, status, version
                    ) VALUES (
                        'UPGRADE-RECEIPT', CURRENT_TIMESTAMP, 'Supplier original name',
                        (SELECT id FROM suppliers WHERE code = 'UPGRADE-SUPPLIER'),
                        10.0000, CURRENT_TIMESTAMP, 'upgrade-test',
                        'upgrade-receipt-key', REPEAT('1', 64), 'POSTED', 0
                    )
                    """);
                statement.executeUpdate("""
                    INSERT INTO supplier_payments (
                        goods_receipt_id, supplier_id, cash_session_id, amount,
                        payment_method, paid_at, actor, is_voided, idempotency_key,
                        request_hash, created_at, version
                    ) VALUES (
                        (SELECT id FROM goods_receipts WHERE code = 'UPGRADE-RECEIPT'),
                        (SELECT id FROM suppliers WHERE code = 'UPGRADE-SUPPLIER'),
                        NULL, 2.0000, 'BANK_TRANSFER', CURRENT_TIMESTAMP,
                        'upgrade-test', FALSE, 'upgrade-payment-key',
                        REPEAT('2', 64), CURRENT_TIMESTAMP, 0
                    )
                    """);
                statement.execute("CREATE TABLE item_audit_logs (id BIGINT PRIMARY KEY)");
                statement.executeUpdate("INSERT INTO item_audit_logs (id) VALUES (1)");
                statement.execute("""
                    CREATE TABLE stock_movement_legacy_audit_links (
                        stock_movement_id BIGINT PRIMARY KEY
                    )
                    """);
            }

            Flyway upgraded = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:migration")
                .load();

            assertThatThrownBy(upgraded::migrate)
                .rootCause()
                .hasMessageContaining("Cannot remove item_audit_logs: 1 rows remain");
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM item_audit_logs");
            }
            upgraded.migrate();

            assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("21");
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                assertThat(columnExists(statement, "items", "stock_quantity")).isFalse();
                assertThat(columnExists(statement, "goods_receipts", "supplier_name")).isFalse();
                assertThat(columnExists(
                    statement, "goods_receipts", "supplier_name_snapshot")).isTrue();
                assertThat(tableExists(statement, "item_audit_logs")).isFalse();
                assertThat(tableExists(
                    statement, "stock_movement_legacy_audit_links")).isFalse();

                try (ResultSet result = statement.executeQuery("""
                        SELECT stock_store, stock_warehouse
                        FROM items
                        WHERE id = 1
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBigDecimal("stock_store"))
                        .isEqualByComparingTo(new BigDecimal("12.5000"));
                    assertThat(result.getBigDecimal("stock_warehouse"))
                        .isEqualByComparingTo(new BigDecimal("3.2500"));
                }
                assertThat(singleString(statement, """
                    SELECT supplier_name_snapshot
                    FROM goods_receipts
                    WHERE code = 'UPGRADE-RECEIPT'
                    """)).isEqualTo("Supplier original name");
                assertThat(singleLong(statement, """
                    SELECT COUNT(*) FROM stock_movements
                    WHERE reference_no = 'UPGRADE-OPENING-1'
                    """)).isEqualTo(1L);
                assertThat(singleLong(statement, """
                    SELECT COUNT(*) FROM supplier_payments
                    WHERE idempotency_key = 'upgrade-payment-key'
                    """)).isEqualTo(1L);
            }
        } finally {
            postgres.stop();
        }
    }

    private boolean columnExists(Statement statement, String table, String column)
            throws Exception {
        return singleBoolean(statement, """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '%s'
                  AND column_name = '%s'
            )
            """.formatted(table, column));
    }

    private boolean tableExists(Statement statement, String table) throws Exception {
        return singleBoolean(statement, """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = '%s'
            )
            """.formatted(table));
    }

    private boolean singleBoolean(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
