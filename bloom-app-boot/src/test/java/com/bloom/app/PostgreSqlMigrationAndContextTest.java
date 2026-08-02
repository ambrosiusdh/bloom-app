package com.bloom.app;

import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.model.DocumentCounter;
import com.bloom.app.persistence.repository.DocumentCounterRepository;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgreSqlMigrationAndContextTest {

    private static final String EXTERNAL_DATABASE_URL =
        System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentCounterRepository documentCounterRepository;

    @Autowired
    private ItemCategoryCounterRepository itemCategoryCounterRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            registry.add(
                "spring.datasource.username",
                () -> System.getProperty("bloom.test.database.username", "postgres")
            );
            registry.add(
                "spring.datasource.password",
                () -> System.getProperty("bloom.test.database.password", "postgres")
            );
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void appliesAllMigrationsAndBackfillsLegacyStockIntoStore() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        List<Map<String, Object>> stockRows = jdbcTemplate.queryForList("""
            SELECT sku, stock_quantity, stock_store, stock_warehouse,
                   base_unit_of_measure, fractional_quantity_allowed
            FROM items
            ORDER BY id
            """);

        assertThat(stockRows).hasSize(6);
        assertThat(stockRows).allSatisfy(row -> {
            assertThat(row.get("stock_store")).isEqualTo(row.get("stock_quantity"));
            assertThat(row.get("stock_warehouse")).isEqualTo(0);
            assertThat(row.get("base_unit_of_measure")).isEqualTo("PIECE");
            assertThat(row.get("fractional_quantity_allowed")).isEqualTo(false);
        });

        assertThat(tableExists("item_audit_logs")).isTrue();
        assertThat(columnExists("items", "stock_quantity")).isTrue();
        assertThat(tableExists("suppliers")).isTrue();
        assertThat(tableExists("cash_sessions")).isTrue();
        assertThat(tableExists("expenses")).isTrue();
        assertThat(columnExists("cash_sessions", "version")).isTrue();
        assertThat(columnExists("expenses", "version")).isTrue();
        assertThat(columnExists("item_category_counters", "version")).isFalse();
        assertThat(constraintExists(
            "item_category_counters",
            "uq_item_category_counters_category"
        )).isTrue();
        assertThat(constraintExists(
            "item_category_counters",
            "uq_item_category_counters_category_sequence"
        )).isFalse();

        assertThat(numericScale("items", "price")).isEqualTo(4);
        assertThat(numericScale("sales", "subtotal_amount")).isEqualTo(4);
        assertThat(numericScale("sales", "discount_amount")).isEqualTo(4);
        assertThat(numericScale("sales", "total_amount")).isEqualTo(4);
        assertThat(numericScale("sales", "paid_amount")).isEqualTo(4);
        assertThat(numericScale("sale_items", "unit_price")).isEqualTo(4);
        assertThat(numericScale("sale_items", "subtotal")).isEqualTo(4);
        assertThat(numericScale("goods_receipts", "total_amount")).isEqualTo(4);
        assertThat(numericScale("goods_receipts", "paid_amount")).isEqualTo(4);
        assertThat(numericScale("goods_receipt_items", "purchase_price")).isEqualTo(4);
        assertThat(numericScale("cash_sessions", "opening_cash")).isEqualTo(4);
        assertThat(numericScale("cash_sessions", "closing_cash")).isEqualTo(4);
        assertThat(numericScale("expenses", "amount")).isEqualTo(4);
    }

    @Test
    void loadsApplicationContextWithHibernateValidationAndStringDocumentType() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(ItemCategoryCounterRepository.class)).isNotNull();

        DocumentCounter counter = documentCounterRepository.saveAndFlush(
            DocumentCounter.builder()
                .documentType(DocumentType.SALE)
                .year(2026)
                .month(7)
                .currentSequence(1L)
                .build()
        );

        String storedDocumentType = jdbcTemplate.queryForObject(
            "SELECT document_type FROM document_counters WHERE id = ?",
            String.class,
            counter.getId()
        );
        assertThat(storedDocumentType).isEqualTo("SALE");
    }

    @Test
    void allocatesItemCategorySequencesAtomically() {
        String categoryCode = "CONCURRENT-" + UUID.randomUUID();
        Long categoryId = jdbcTemplate.queryForObject(
            """
            INSERT INTO item_categories (name, code, active)
            VALUES ('Concurrent allocation test', ?, TRUE)
            RETURNING id
            """,
            Long.class,
            categoryCode
        );

        int allocationCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Long> allocatedSequences;
        try {
            List<CompletableFuture<Long>> allocations = Stream.generate(() ->
                    CompletableFuture.supplyAsync(
                        () -> itemCategoryCounterRepository.incrementAndGetSequence(categoryId),
                        executor
                    )
                )
                .limit(allocationCount)
                .toList();

            allocatedSequences = allocations.stream()
                .map(CompletableFuture::join)
                .toList();
        } finally {
            executor.shutdownNow();
        }

        assertThat(allocatedSequences).containsExactlyInAnyOrderElementsOf(
            LongStream.rangeClosed(1, allocationCount).boxed().toList()
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_sequence FROM item_category_counters WHERE item_category_id = ?",
            Long.class,
            categoryId
        )).isEqualTo(allocationCount);
    }

    @Test
    void enforcesSingleGlobalOpenCashSessionAndRequiredExpenseSession() {
        assertThat(columnIsNullable("expenses", "cash_session_id")).isFalse();

        jdbcTemplate.update("""
            INSERT INTO cash_sessions (user_id, opening_cash, status, opened_at, version)
            VALUES (1, 100.0000, 'OPEN', CURRENT_TIMESTAMP, 0)
            """);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO cash_sessions (user_id, opening_cash, status, opened_at, version)
            VALUES (1, 200.0000, 'OPEN', CURRENT_TIMESTAMP, 0)
            """))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
            )
            """,
            Boolean.class,
            tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    private int numericScale(String tableName, String columnName) {
        Integer scale = jdbcTemplate.queryForObject(
            """
            SELECT numeric_scale
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
              AND column_name = ?
            """,
            Integer.class,
            tableName,
            columnName
        );
        return scale == null ? -1 : scale;
    }

    private boolean columnIsNullable(String tableName, String columnName) {
        String nullable = jdbcTemplate.queryForObject(
            """
            SELECT is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
              AND column_name = ?
            """,
            String.class,
            tableName,
            columnName
        );
        return "YES".equals(nullable);
    }

    private boolean columnExists(String tableName, String columnName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            )
            """,
            Boolean.class,
            tableName,
            columnName
        );
        return Boolean.TRUE.equals(exists);
    }

    private boolean constraintExists(String tableName, String constraintName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND constraint_name = ?
            )
            """,
            Boolean.class,
            tableName,
            constraintName
        );
        return Boolean.TRUE.equals(exists);
    }

    private static PostgreSQLContainer<?> startPostgresWhenRequired() {
        if (EXTERNAL_DATABASE_URL != null) {
            return null;
        }

        PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");
        postgres.start();
        return postgres;
    }
}
