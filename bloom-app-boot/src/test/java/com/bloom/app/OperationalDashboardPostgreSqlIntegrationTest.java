package com.bloom.app;

import com.bloom.app.persistence.repository.ExpenseRepository;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OperationalDashboardPostgreSqlIntegrationTest {
    private static final String EXTERNAL_DATABASE_URL =
        System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            registry.add("spring.datasource.username",
                () -> System.getProperty("bloom.test.database.username", "postgres"));
            registry.add("spring.datasource.password",
                () -> System.getProperty("bloom.test.database.password", "postgres"));
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void salesAggregateUsesExactHalfOpenBoundariesAndPreservesDecimalPrecision() {
        long sessionId = openSession();
        Instant start = Instant.parse("2040-01-01T17:00:00Z");
        Instant end = Instant.parse("2040-01-02T17:00:00Z");
        insertSale(sessionId, start.minusNanos(1_000), "1.1111");
        insertSale(sessionId, start, "2.2222");
        insertSale(sessionId, end.minusNanos(1_000), "3.3333");
        insertSale(sessionId, end, "4.4444");

        var totals = saleRepository.summarizeOperationalSales(start, end);

        assertThat(totals.getSalesAmount()).isEqualByComparingTo("5.5555");
        assertThat(totals.getTransactionCount()).isEqualTo(2);
    }

    @Test
    void activeExpenseAggregateExcludesVoidsAndIncludesOwnerWithdrawals() {
        long sessionId = openSession();
        insertExpense(sessionId, "OWNER_WITHDRAWAL", "12.3456", false);
        insertExpense(sessionId, "FOOD_AND_DRINK", "0.0001", false);
        insertExpense(sessionId, "STORE_OPERATIONAL", "99.9999", true);

        var totals = expenseRepository.summarizeActiveDrawerExpenses(sessionId);

        assertThat(totals.getActiveExpenseAmount()).isEqualByComparingTo("12.3457");
        assertThat(totals.getActiveExpenseCount()).isEqualTo(2);
    }

    @Test
    void payablesAggregatePreAggregatesPaymentsAndExcludesCancelledReceiptsAndVoids() {
        long supplierOne = insertSupplier();
        long supplierTwo = insertSupplier();
        long first = insertReceipt(supplierOne, "100.0000", "POSTED");
        long fullyPaid = insertReceipt(supplierOne, "50.0000", "POSTED");
        long secondSupplier = insertReceipt(supplierTwo, "80.0000", "POSTED");
        insertReceipt(supplierTwo, "90.0000", "CANCELLED");

        insertPayment(first, supplierOne, "10.1111", false);
        insertPayment(first, supplierOne, "20.2222", false);
        insertPayment(first, supplierOne, "5.0000", true);
        insertPayment(fullyPaid, supplierOne, "50.0000", false);
        insertPayment(secondSupplier, supplierTwo, "0.0001", false);

        var totals = goodsReceiptRepository.summarizeOperationalPayables();

        assertThat(totals.getOutstandingAmount()).isEqualByComparingTo("149.6666");
        assertThat(totals.getOpenReceiptCount()).isEqualTo(2);
    }

    @Test
    void emptyAggregateTablesReturnZerosInsteadOfNull() {
        var sales = saleRepository.summarizeOperationalSales(
            Instant.parse("2099-01-01T00:00:00Z"),
            Instant.parse("2099-01-02T00:00:00Z"));
        var expenses = expenseRepository.summarizeActiveDrawerExpenses(Long.MAX_VALUE);
        var payables = goodsReceiptRepository.summarizeOperationalPayables();

        assertThat(sales.getSalesAmount()).isEqualByComparingTo("0");
        assertThat(sales.getTransactionCount()).isZero();
        assertThat(expenses.getActiveExpenseAmount()).isEqualByComparingTo("0");
        assertThat(expenses.getActiveExpenseCount()).isZero();
        assertThat(payables.getOutstandingAmount()).isEqualByComparingTo("0");
        assertThat(payables.getOpenReceiptCount()).isZero();
    }

    private long openSession() {
        return jdbcTemplate.queryForObject("""
            INSERT INTO cash_sessions (
                opened_by_id, opening_cash, expected_closing_cash, status, opened_at, version
            ) VALUES (
                (SELECT id FROM users WHERE username = 'admin'),
                0.0000, 0.0000, 'OPEN', CURRENT_TIMESTAMP, 0
            ) RETURNING id
            """, Long.class);
    }

    private void insertSale(long sessionId, Instant createdAt, String amount) {
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO sales (
                code, subtotal_amount, discount_amount, total_amount, paid_amount,
                change_amount, payment_type, cash_session_id, checkout_idempotency_key,
                checkout_request_hash, created_at, updated_at, created_by, updated_by
            ) VALUES (?, ?::numeric, 0.0000, ?::numeric, ?::numeric,
                0.0000, 'QRIS', ?, ?, REPEAT('a', 64), ?, ?, 'test', 'test')
            """, "DASH-SALE-" + suffix, amount, amount, amount, sessionId,
            "dash-sale-key-" + suffix, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertExpense(long sessionId, String category, String amount, boolean voided) {
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO expenses (
                cash_session_id, create_idempotency_key, create_request_hash, amount,
                category, description, is_voided, voided_reason, voided_at, voided_by,
                created_at, created_by, version
            ) VALUES (?, ?, REPEAT('b', 64), ?::numeric, ?, 'dashboard test', ?,
                CASE WHEN ? THEN 'test void' ELSE NULL END,
                CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                CASE WHEN ? THEN 'test' ELSE NULL END,
                CURRENT_TIMESTAMP, 'test', 0)
            """, sessionId, "dash-expense-" + suffix, amount, category, voided,
            voided, voided, voided);
    }

    private long insertSupplier() {
        String suffix = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return jdbcTemplate.queryForObject("""
            INSERT INTO suppliers (name, code, active, created_at, updated_at, version)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            RETURNING id
            """, Long.class, "Dashboard supplier " + suffix, "DASH" + suffix);
    }

    private long insertReceipt(long supplierId, String total, String status) {
        String suffix = UUID.randomUUID().toString();
        boolean cancelled = "CANCELLED".equals(status);
        return jdbcTemplate.queryForObject("""
            INSERT INTO goods_receipts (
                code, received_date, supplier_id, supplier_name_snapshot, create_idempotency_key,
                create_request_hash, total_amount, status, description,
                cancelled_at, cancelled_by, cancellation_reason, created_at, created_by, version
            ) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 day', ?, 'Dashboard supplier', ?,
                REPEAT('c', 64), ?::numeric, ?, 'dashboard test',
                CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                CASE WHEN ? THEN 'test' ELSE NULL END,
                CASE WHEN ? THEN 'test cancellation' ELSE NULL END,
                CURRENT_TIMESTAMP, 'test', 0)
            RETURNING id
            """, Long.class, "DASH-RECEIPT-" + suffix, supplierId,
            "dash-receipt-key-" + suffix, total, status, cancelled, cancelled, cancelled);
    }

    private void insertPayment(long receiptId, long supplierId, String amount, boolean voided) {
        String suffix = UUID.randomUUID().toString();
        long paymentId = jdbcTemplate.queryForObject("""
            INSERT INTO supplier_payments (
                goods_receipt_id, supplier_id, amount, payment_method, paid_at,
                reference, note, actor, is_voided, idempotency_key, request_hash,
                created_at, version
            ) VALUES (?, ?, ?::numeric, 'BANK_TRANSFER', CURRENT_TIMESTAMP,
                'dashboard test', NULL, 'test', FALSE, ?, REPEAT('d', 64),
                CURRENT_TIMESTAMP, 0)
            RETURNING id
            """, Long.class, receiptId, supplierId, amount, "dash-payment-" + suffix);
        if (voided) {
            jdbcTemplate.update("""
                UPDATE supplier_payments
                SET is_voided = TRUE, void_reason = 'test void',
                    voided_at = CURRENT_TIMESTAMP, voided_by = 'test', version = 1
                WHERE id = ?
                """, paymentId);
        }
    }

    private static PostgreSQLContainer<?> startPostgresWhenRequired() {
        if (EXTERNAL_DATABASE_URL != null) {
            return null;
        }
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
        postgres.start();
        return postgres;
    }
}
