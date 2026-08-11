package com.bloom.app;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.request.stocktransfer.FilterStockTransferRequest;
import com.bloom.app.api.dto.request.stocktransfer.StockTransferLineRequest;
import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferSummaryResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.exception.IdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.DocumentCounter;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.model.StockTransfer;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.DocumentCounterRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.persistence.repository.StockTransferRepository;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.StockAdjustmentService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.service.StockTransferService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private StockMovementQueryService stockMovementQueryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private SaleService saleService;

    @Autowired
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Autowired
    private StockTransferRepository stockTransferRepository;

    @Autowired
    private StockTransferService stockTransferService;

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
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void appliesAllMigrationsAndBackfillsLegacyStockIntoStore() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");

        List<Map<String, Object>> stockRows = jdbcTemplate.queryForList("""
            SELECT sku, stock_quantity, stock_store, stock_warehouse,
                   base_unit_of_measure, fractional_quantity_allowed
            FROM items
            WHERE id BETWEEN 1 AND 6
            ORDER BY id
            """);

        assertThat(stockRows).hasSize(6);
        assertThat(stockRows).allSatisfy(row -> {
            assertThat((BigDecimal) row.get("stock_store")).isEqualByComparingTo((BigDecimal) row.get("stock_quantity"));
            assertThat((BigDecimal) row.get("stock_store")).hasScaleOf(4);
            assertThat((BigDecimal) row.get("stock_quantity")).hasScaleOf(4);
            assertThat((BigDecimal) row.get("stock_warehouse")).isEqualByComparingTo("0.0000");
            assertThat(row.get("base_unit_of_measure")).isEqualTo("PIECE");
            assertThat(row.get("fractional_quantity_allowed")).isEqualTo(false);
        });

        List<Map<String, Object>> demoStockRows = jdbcTemplate.queryForList("""
            SELECT
                item.sku,
                item.stock_store,
                item.stock_warehouse,
                COALESCE(SUM(CASE
                    WHEN movement.stock_location = 'STORE'
                        THEN CASE movement.movement_type
                            WHEN 'IN' THEN movement.quantity
                            ELSE -movement.quantity
                        END
                    ELSE 0
                END), 0) AS ledger_store,
                COALESCE(SUM(CASE
                    WHEN movement.stock_location = 'WAREHOUSE'
                        THEN CASE movement.movement_type
                            WHEN 'IN' THEN movement.quantity
                            ELSE -movement.quantity
                        END
                    ELSE 0
                END), 0) AS ledger_warehouse
            FROM items item
            LEFT JOIN stock_movements movement ON movement.product_id = item.id
            WHERE item.sku LIKE 'DEMO-%'
            GROUP BY item.id, item.sku, item.stock_store, item.stock_warehouse
            ORDER BY item.sku
            """);

        assertThat(demoStockRows).hasSize(6);
        assertThat(demoStockRows).allSatisfy(row -> {
            assertThat((BigDecimal) row.get("stock_store"))
                .isEqualByComparingTo((BigDecimal) row.get("ledger_store"));
            assertThat((BigDecimal) row.get("stock_warehouse"))
                .isEqualByComparingTo((BigDecimal) row.get("ledger_warehouse"));
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE code LIKE 'SALE/DEMO/%'", Long.class))
            .isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goods_receipts WHERE code LIKE 'GR/DEMO/%'", Long.class))
            .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stock_transfers WHERE code LIKE 'ST/DEMO/%'", Long.class))
            .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expenses", Long.class)).isEqualTo(3L);

        assertThat(tableExists("item_audit_logs")).isTrue();
        assertThat(tableExists("stock_movement_legacy_audit_links")).isTrue();
        assertThat(columnExists("stock_movements", "reference_no")).isTrue();
        assertThat(columnExists("stock_movements", "adjustment_action_type")).isTrue();
        assertThat(columnExists("items", "stock_quantity")).isTrue();
        assertThat(tableExists("suppliers")).isTrue();
        assertThat(tableExists("cash_sessions")).isTrue();
        assertThat(tableExists("expenses")).isTrue();
        assertThat(tableExists("stock_transfers")).isTrue();
        assertThat(tableExists("stock_transfer_lines")).isTrue();
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
        assertThat(constraintExists(
            "stock_movements",
            "chk_stock_movements_balance_equation"
        )).isTrue();
        assertThat(indexExists("uq_stock_movements_sale_item_location")).isTrue();
        assertThat(indexExists("uq_stock_movements_transfer_item_location")).isTrue();
        assertThat(indexExists("idx_stock_transfers_history_order")).isTrue();
        assertThat(indexExists("idx_stock_transfer_lines_item_transfer")).isTrue();
        assertThat(indexExists("idx_stock_movements_history_order")).isTrue();
        assertThat(indexExists("idx_stock_movements_product_history")).isTrue();

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
        assertThat(numericScale("items", "stock_quantity")).isEqualTo(4);
        assertThat(numericScale("items", "stock_store")).isEqualTo(4);
        assertThat(numericScale("items", "stock_warehouse")).isEqualTo(4);
        assertThat(numericScale("sale_items", "quantity")).isEqualTo(4);
        assertThat(numericScale("goods_receipt_items", "quantity")).isEqualTo(4);
        assertThat(numericScale("stock_adjustment_items", "change_quantity")).isEqualTo(4);
        assertThat(numericScale("stock_adjustment_items", "previous_stock")).isEqualTo(4);
        assertThat(numericScale("stock_adjustment_items", "new_stock")).isEqualTo(4);
        assertThat(numericScale("stock_movements", "quantity")).isEqualTo(4);
        assertThat(numericScale("stock_movements", "qty_before")).isEqualTo(4);
        assertThat(numericScale("stock_movements", "qty_after")).isEqualTo(4);
        assertThat(numericScale("item_audit_logs", "qty")).isEqualTo(4);
        assertThat(numericScale("item_audit_logs", "qty_before")).isEqualTo(4);
        assertThat(numericScale("item_audit_logs", "qty_after")).isEqualTo(4);
        assertThat(numericScale("stock_transfer_lines", "quantity")).isEqualTo(4);
        assertThat(columnExists("stock_transfer_lines", "item_sku")).isTrue();
        assertThat(columnExists("stock_transfer_lines", "item_name")).isTrue();
        assertThat(columnExists("stock_transfer_lines", "unit_of_measure")).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE items SET stock_store = -0.0001 WHERE id = 1
            """))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO stock_movements (
                product_id, movement_type, source_type, source_id, quantity,
                created_at, stock_location, qty_before, qty_after
            ) VALUES (1, 'IN', 'OPENING_BALANCE', 1, 1.0000,
                      CURRENT_TIMESTAMP, 'STORE', 0.0000, 2.0000)
            """))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v13ReconcilesPreCutoverAuditRowsWithoutMutatingHistoricalMovement() {
        String schema = "audit_cutover_" + UUID.randomUUID().toString().replace("-", "");
        if (!schema.startsWith("audit_cutover_")) {
            throw new IllegalStateException("Unsafe test schema name");
        }
        jdbcTemplate.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure()
                .dataSource(Objects.requireNonNull(jdbcTemplate.getDataSource()))
                .locations("classpath:migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("12"))
                .load()
                .migrate();

            Long saleId = jdbcTemplate.queryForObject(
                "INSERT INTO " + schema + ".sales (code, payment_type, created_at) "
                    + "VALUES ('SALE/PRE-CUTOVER', 'CASH', CURRENT_TIMESTAMP) RETURNING id",
                Long.class
            );
            Long movementId = jdbcTemplate.queryForObject(
                """
                INSERT INTO %s.stock_movements (
                    product_id, movement_type, source_type, source_id,
                    quantity, created_at, stock_location, qty_before, qty_after
                ) VALUES (1, 'OUT', 'SALE', ?, 1.0000, CURRENT_TIMESTAMP, 'STORE', 2.0000, 1.0000)
                RETURNING id
                """.formatted(schema),
                Long.class,
                saleId
            );
            Long auditId = jdbcTemplate.queryForObject(
                """
                INSERT INTO %s.item_audit_logs (
                    item_id, qty, qty_before, qty_after, source, reference_no, created_date
                ) VALUES (1, 1.0000, 2.0000, 1.0000, 'SALE', 'SALE/PRE-CUTOVER', CURRENT_TIMESTAMP)
                RETURNING id
                """.formatted(schema),
                Long.class
            );

            Flyway.configure()
                .dataSource(Objects.requireNonNull(jdbcTemplate.getDataSource()))
                .locations("classpath:migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.LATEST)
                .load()
                .migrate();

            assertThat(jdbcTemplate.queryForObject(
                "SELECT item_audit_log_id FROM " + schema
                    + ".stock_movement_legacy_audit_links WHERE stock_movement_id = ?",
                Long.class,
                movementId
            )).isEqualTo(auditId);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT reference_no FROM " + schema + ".stock_movements WHERE id = ?",
                String.class,
                movementId
            )).isNull();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
        }
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

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cash_sessions WHERE status = 'OPEN'", Long.class))
            .isEqualTo(1L);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO cash_sessions (user_id, opening_cash, status, opened_at, version)
            VALUES (1, 200.0000, 'OPEN', CURRENT_TIMESTAMP, 0)
            """))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsOversizedLedgerReferenceBeforeChangingBalance() {
        Long itemId = insertInventoryItem("ROLLBACK", new BigDecimal("1.0000"));
        Item item = itemRepository.findById(itemId).orElseThrow();
        Sale sale = Sale.builder()
            .id(itemId)
            .code("X".repeat(101))
            .items(List.of(SaleItem.builder()
                .item(item)
                .quantity(new BigDecimal("1.0000"))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();

        assertThatThrownBy(() -> stockMovementService.recordSaleMovements(sale))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Movement reference must not exceed 100 characters");

        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("1.0000");
        assertThat(stockMovementRepository.findByProductId(itemId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_audit_logs WHERE item_id = ?", Long.class, itemId))
            .isZero();
    }

    @Test
    void filtersStockMovementRepositoryWithoutNPlusOneQueriesAndLeavesLegacyAuditUnwritten() {
        Long itemId = insertInventoryItem("LEDGER-QUERY", new BigDecimal("5.0000"));
        Long otherItemId = insertInventoryItem("LEDGER-QUERY-OTHER", new BigDecimal("5.0000"));
        String sku = jdbcTemplate.queryForObject(
            "SELECT sku FROM items WHERE id = ?", String.class, itemId);
        Instant first = Instant.parse("2098-08-10T10:00:00Z");
        Instant second = Instant.parse("2098-08-11T10:00:00Z");

        insertStockMovement(itemId, MovementType.OUT, MovementSourceType.SALE, 91L,
            StockLocation.STORE, first, "SALE-REF-91");
        insertStockMovement(itemId, MovementType.OUT, MovementSourceType.SALE, 92L,
            StockLocation.STORE, second, "SALE-REF-92");
        insertStockMovement(otherItemId, MovementType.OUT, MovementSourceType.SALE, 93L,
            StockLocation.STORE, second, "SALE-REF-93");
        insertStockMovement(itemId, MovementType.IN, MovementSourceType.RETURN, 94L,
            StockLocation.WAREHOUSE, second, "RETURN-REF-94");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Page<StockMovementResponse> result = stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder()
                .itemId(itemId)
                .itemSku("  " + sku.toLowerCase() + "  ")
                .sourceType(MovementSourceType.SALE)
                .movementType(MovementType.OUT)
                .location(StockLocation.STORE)
                .startDate(Instant.parse("2098-08-10T00:00:00Z"))
                .endDate(Instant.parse("2098-08-12T00:00:00Z"))
                .reference(" ref ")
                .build(),
            PageRequest.of(0, 1)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).singleElement().satisfies(movement -> {
            assertThat(movement.getReferenceNo()).isEqualTo("SALE-REF-92");
            assertThat(movement.getItem().getSku()).isEqualTo(sku);
            assertThat(movement.getItem().getCategory()).isNotNull();
            assertThat(movement.getLocation()).isEqualTo(StockLocation.STORE);
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
        assertThat(stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder().itemId(itemId).reference("_").build(),
            PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder().itemId(itemId).reference("%").build(),
            PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_audit_logs WHERE item_id IN (?, ?)",
            Long.class, itemId, otherItemId)).isZero();
    }

    @Test
    void derivesHistoricalReferenceAndCorrectionActionWithoutMutatingMovement() {
        Long itemId = insertInventoryItem("HISTORICAL-LEDGER", new BigDecimal("5.0000"));
        long sharedSourceId = 9_000_000L + itemId;
        String saleCode = "SALE/HISTORICAL/" + UUID.randomUUID();
        Long saleId = jdbcTemplate.queryForObject(
            """
            INSERT INTO sales (id, code, payment_type, created_at)
            VALUES (?, ?, 'CASH', ?)
            RETURNING id
            """,
            Long.class,
            sharedSourceId,
            saleCode,
            Timestamp.from(Instant.parse("2026-07-01T10:00:00Z"))
        );
        Long historicalMovementId = insertStockMovement(
            itemId, MovementType.OUT, MovementSourceType.SALE, saleId,
            StockLocation.STORE, Instant.parse("2026-07-01T10:00:00Z"), null);
        Long legacyAuditId = jdbcTemplate.queryForObject(
            """
            INSERT INTO item_audit_logs (
                item_id, qty, qty_before, qty_after, source,
                reference_no, created_by, created_date
            ) VALUES (?, 1.0000, 2.0000, 1.0000, 'SALE', ?, 'legacy', ?)
            RETURNING id
            """,
            Long.class,
            itemId,
            saleCode,
            Timestamp.from(Instant.parse("2026-07-01T10:00:00Z"))
        );
        jdbcTemplate.update(
            """
            INSERT INTO stock_movement_legacy_audit_links (stock_movement_id, item_audit_log_id)
            VALUES (?, ?)
            """,
            historicalMovementId,
            legacyAuditId
        );

        Page<StockMovementResponse> historicalSale = stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder().itemId(itemId).reference(saleCode).build(),
            PageRequest.of(0, 10));
        assertThat(historicalSale.getContent()).singleElement()
            .satisfies(movement -> {
                assertThat(movement.getReferenceNo()).isEqualTo(saleCode);
                assertThat(movement.getLegacyAuditLogId()).isEqualTo(legacyAuditId);
            });

        String adjustmentCode = "SA/HISTORICAL/" + UUID.randomUUID();
        Long adjustmentId = jdbcTemplate.queryForObject(
            """
            INSERT INTO stock_adjustments (
                id, stock_adjustment_code, reason, created_by, created_at
            ) VALUES (?, ?, 'Legacy correction', 'legacy', ?)
            RETURNING id
            """,
            Long.class,
            sharedSourceId,
            adjustmentCode,
            Timestamp.from(Instant.parse("2026-07-02T10:00:00Z"))
        );
        jdbcTemplate.update(
            """
            INSERT INTO stock_adjustment_items (
                stock_adjustment_id, item_id, action_type, change_quantity,
                previous_stock, new_stock, stock_location
            ) VALUES (?, ?, 'CORRECTION', 1.0000, 2.0000, 1.0000, 'STORE')
            """,
            adjustmentId,
            itemId
        );
        Long historicalCorrectionId = insertStockMovement(
            itemId, MovementType.OUT, MovementSourceType.STOCK_ADJUSTMENT,
            adjustmentId, StockLocation.STORE, Instant.parse("2026-07-02T10:00:00Z"), null);

        Page<StockMovementResponse> historicalCorrection = stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder()
                .itemId(itemId)
                .reference(adjustmentCode)
                .adjustmentActionType(StockAdjustmentActionType.CORRECTION)
                .build(),
            PageRequest.of(0, 10));
        assertThat(historicalCorrection.getContent()).singleElement().satisfies(movement -> {
            assertThat(movement.getReferenceNo()).isEqualTo(adjustmentCode);
            assertThat(movement.getAdjustmentActionType())
                .isEqualTo(StockAdjustmentActionType.CORRECTION);
        });

        assertThat(adjustmentId).isEqualTo(saleId);
        Page<StockMovementResponse> historicalSaleAfterAdjustment =
            stockMovementQueryService.filterMovements(
            FilterStockMovementRequest.builder().reference(saleCode).build(),
            PageRequest.of(0, 10));
        assertThat(historicalSaleAfterAdjustment.getContent()).singleElement()
            .satisfies(movement -> assertThat(movement.getAdjustmentActionType()).isNull());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stock_movements WHERE id IN (?, ?) AND reference_no IS NOT NULL",
            Long.class, historicalMovementId, historicalCorrectionId)).isZero();
    }

    @Test
    void oneOfTwoConcurrentSaleDeductionsFailsWithDomainConflict() throws Exception {
        Long itemId = insertInventoryItem("CONCURRENCY", new BigDecimal("1.0000"));
        Item firstView = itemRepository.findById(itemId).orElseThrow();
        Item secondView = itemRepository.findById(itemId).orElseThrow();
        Sale firstSale = concurrentSale(itemId + 10_000, firstView);
        Sale secondSale = concurrentSale(itemId + 20_000, secondView);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<RuntimeException> outcomes;
        try {
            List<CompletableFuture<RuntimeException>> attempts = List.of(firstSale, secondSale).stream()
                .map(sale -> CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        stockMovementService.recordSaleMovements(sale);
                        return null;
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return new IllegalStateException(exception);
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }, executor))
                .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = attempts.stream().map(CompletableFuture::join).toList();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(StockConcurrencyException.class::isInstance).hasSize(1);
        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("0.2500");
        assertThat(stockMovementRepository.findByProductId(itemId)).hasSize(1);
    }

    @Test
    @Transactional
    void createsAggregatedFractionalSaleWithSnapshotsAndOneDeduction() {
        Long itemId = insertInventoryItem("SALE-INTEGRATION", new BigDecimal("1.0000"));
        Item item = itemRepository.findById(itemId).orElseThrow();
        CreateSaleRequest request = CreateSaleRequest.builder()
            .paidAmount(new BigDecimal("7.5000"))
            .discountAmount(BigDecimal.ZERO)
            .paymentType(PaymentType.CASH)
            .saleItemList(List.of(
                saleLine(item.getSku(), "0.2500"),
                saleLine(item.getSku(), "0.5000")
            ))
            .build();

        SaleResponse response = saleService.createSale(request);

        Sale savedSale = saleRepository.findByCode(response.getCode()).orElseThrow();
        assertThat(savedSale.getItems()).hasSize(1);
        assertThat(savedSale.getItems().getFirst().getQuantity()).isEqualByComparingTo("0.7500");
        assertThat(savedSale.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(savedSale.getItems().getFirst().getSubtotal()).isEqualByComparingTo("7.5000");
        assertThat(savedSale.getTotalAmount()).isEqualByComparingTo("7.5000");
        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("0.2500");

        var movements = stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.SALE, savedSale.getId());
        assertThat(movements).singleElement().satisfies(movement -> {
            assertThat(movement.getMovementType()).isEqualTo(MovementType.OUT);
            assertThat(movement.getQuantity()).isEqualByComparingTo("0.7500");
            assertThat(movement.getQtyBefore()).isEqualByComparingTo("1.0000");
            assertThat(movement.getQtyAfter()).isEqualByComparingTo("0.2500");
        });

        stockMovementService.recordSaleMovements(savedSale);
        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("0.2500");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.SALE, savedSale.getId())).hasSize(1);
    }

    @Test
    @Transactional
    void createsCorrectionAsAbsoluteTargetWithDerivedMovement() {
        Long itemId = insertInventoryItem("CORRECTION-INTEGRATION", new BigDecimal("1.0000"));
        Item item = itemRepository.findById(itemId).orElseThrow();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
            .reason("Physical count")
            .items(List.of(StockAdjustmentItemRequest.builder()
                .itemSku(item.getSku())
                .changeQuantity(new BigDecimal("0.2500"))
                .actionType(StockAdjustmentActionType.CORRECTION)
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();

        StockAdjustmentResponse response = stockAdjustmentService.createStockAdjustment(request);

        var adjustment = stockAdjustmentRepository
            .findByStockAdjustmentCode(response.getStockAdjustmentCode()).orElseThrow();
        assertThat(adjustment.getItems()).singleElement().satisfies(adjustmentItem -> {
            assertThat(adjustmentItem.getChangeQuantity()).isEqualByComparingTo("0.2500");
            assertThat(adjustmentItem.getPreviousStock()).isEqualByComparingTo("1.0000");
            assertThat(adjustmentItem.getNewStock()).isEqualByComparingTo("0.2500");
        });
        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("0.2500");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.STOCK_ADJUSTMENT, adjustment.getId()))
            .singleElement()
            .satisfies(movement -> {
                assertThat(movement.getMovementType()).isEqualTo(MovementType.OUT);
                assertThat(movement.getQuantity()).isEqualByComparingTo("0.7500");
                assertThat(movement.getQtyBefore()).isEqualByComparingTo("1.0000");
                assertThat(movement.getQtyAfter()).isEqualByComparingTo("0.2500");
            });
    }

    @Test
    void createsMultiLineTransferAndReplaysIdenticalRequestWithoutMoreMovements() {
        Long firstItemId = insertInventoryItem("TRANSFER-FIRST", new BigDecimal("5.0000"));
        Long secondItemId = insertInventoryItem("TRANSFER-SECOND", new BigDecimal("3.0000"));
        Item firstItem = itemRepository.findById(firstItemId).orElseThrow();
        Item secondItem = itemRepository.findById(secondItemId).orElseThrow();
        String requestKey = "transfer-" + UUID.randomUUID();
        CreateStockTransferRequest request = transferRequest(
            "Store replenishment",
            transferLine(firstItem.getSku(), "1.2500"),
            transferLine(secondItem.getSku(), "0.5000")
        );

        StockTransferResponse created = stockTransferService.createStockTransfer(requestKey, request);
        StockTransfer saved = stockTransferRepository.findByRequestKey(requestKey).orElseThrow();

        assertThat(created.getId()).isEqualTo(saved.getId());
        assertThat(created.getCode()).isEqualTo(saved.getCode());
        assertThat(created.getRequestKey()).isEqualTo(requestKey);
        assertThat(created.getSourceLocation()).isEqualTo(StockLocation.STORE);
        assertThat(created.getDestinationLocation()).isEqualTo(StockLocation.WAREHOUSE);
        assertThat(created.getLines()).hasSize(2);
        assertThat(created.getLines()).extracting(line -> line.getItemSku())
            .containsExactly(firstItem.getSku(), secondItem.getSku());
        assertThat(created.getLines()).extracting(line -> line.getItemName())
            .containsExactly(firstItem.getName(), secondItem.getName());
        assertThat(created.getLines()).extracting(line -> line.getUnitOfMeasure())
            .containsOnly(UnitOfMeasure.PIECE);
        assertThat(saved.getLines()).extracting(line -> line.getQuantity())
            .containsExactlyInAnyOrder(new BigDecimal("1.2500"), new BigDecimal("0.5000"));

        assertThat(itemRepository.findById(firstItemId).orElseThrow()).satisfies(item -> {
            assertThat(item.getStockStore()).isEqualByComparingTo("3.7500");
            assertThat(item.getStockWarehouse()).isEqualByComparingTo("1.2500");
        });
        assertThat(itemRepository.findById(secondItemId).orElseThrow()).satisfies(item -> {
            assertThat(item.getStockStore()).isEqualByComparingTo("2.5000");
            assertThat(item.getStockWarehouse()).isEqualByComparingTo("0.5000");
        });

        var movements = stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.TRANSFER, saved.getId());
        assertThat(movements).hasSize(4);
        assertThat(movements).allSatisfy(movement -> {
            assertThat(movement.getSourceType()).isEqualTo(MovementSourceType.TRANSFER);
            assertThat(movement.getSourceId()).isEqualTo(saved.getId());
            assertThat(movement.getReferenceNo()).isEqualTo(saved.getCode());
        });
        assertThat(movements).filteredOn(movement -> movement.getMovementType() == MovementType.OUT)
            .extracting(movement -> movement.getStockLocation())
            .containsOnly(StockLocation.STORE);
        assertThat(movements).filteredOn(movement -> movement.getMovementType() == MovementType.IN)
            .extracting(movement -> movement.getStockLocation())
            .containsOnly(StockLocation.WAREHOUSE);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_audit_logs WHERE item_id IN (?, ?)",
            Long.class, firstItemId, secondItemId)).isZero();

        String renamedSku = "RENAMED-" + UUID.randomUUID();
        jdbcTemplate.update(
            "UPDATE items SET name = ?, sku = ?, version = version + 1 WHERE id = ?",
            "Renamed after transfer",
            renamedSku,
            firstItemId
        );

        StockTransferResponse replayed = stockTransferService.createStockTransfer(requestKey, request);
        assertThat(replayed).usingRecursiveComparison().isEqualTo(created);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.TRANSFER, saved.getId())).hasSize(4);

        CreateStockTransferRequest changedRequest = transferRequest(
            "Different semantic request",
            transferLine(firstItem.getSku(), "1.2500"),
            transferLine(secondItem.getSku(), "0.5000")
        );
        assertThatThrownBy(() -> stockTransferService.createStockTransfer(requestKey, changedRequest))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessage("Idempotency key has already been used for a different stock transfer request");
    }

    @Test
    void rollsBackCompleteMultiLineTransferWhenInjectedInboundWriteFails() {
        Long firstItemId = insertInventoryItem("TRANSFER-ROLLBACK-FIRST", new BigDecimal("5.0000"));
        Long failingItemId = insertInventoryItem("TRANSFER-ROLLBACK-SECOND", new BigDecimal("3.0000"));
        Item firstItem = itemRepository.findById(firstItemId).orElseThrow();
        Item failingItem = itemRepository.findById(failingItemId).orElseThrow();
        String requestKey = "transfer-rollback-" + UUID.randomUUID();
        CreateStockTransferRequest request = transferRequest(
            "Injected failure",
            transferLine(firstItem.getSku(), "1.0000"),
            transferLine(failingItem.getSku(), "1.0000")
        );

        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION fail_stock_transfer_inbound()
            RETURNS trigger AS $$
            BEGIN
                IF NEW.source_type = 'TRANSFER'
                   AND NEW.movement_type = 'IN'
                   AND NEW.product_id = %d THEN
                    RAISE EXCEPTION 'injected transfer inbound failure';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """.formatted(failingItemId));
        jdbcTemplate.execute("""
            CREATE TRIGGER trg_fail_stock_transfer_inbound
            BEFORE INSERT ON stock_movements
            FOR EACH ROW EXECUTE FUNCTION fail_stock_transfer_inbound()
            """);

        try {
            assertThatThrownBy(() -> stockTransferService.createStockTransfer(requestKey, request))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("injected transfer inbound failure");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_stock_transfer_inbound ON stock_movements");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_stock_transfer_inbound()");
        }

        assertThat(stockTransferRepository.findByRequestKey(requestKey)).isEmpty();
        assertThat(itemRepository.findById(firstItemId).orElseThrow()).satisfies(item -> {
            assertThat(item.getStockStore()).isEqualByComparingTo("5.0000");
            assertThat(item.getStockWarehouse()).isEqualByComparingTo("0.0000");
        });
        assertThat(itemRepository.findById(failingItemId).orElseThrow()).satisfies(item -> {
            assertThat(item.getStockStore()).isEqualByComparingTo("3.0000");
            assertThat(item.getStockWarehouse()).isEqualByComparingTo("0.0000");
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stock_movements WHERE product_id IN (?, ?)",
            Long.class,
            firstItemId,
            failingItemId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_audit_logs WHERE item_id IN (?, ?)",
            Long.class,
            firstItemId,
            failingItemId
        )).isZero();
    }

    @Test
    void serializesConcurrentIdenticalRequestKeysAndWritesMovementsOnce() throws Exception {
        Long itemId = insertInventoryItem("TRANSFER-CONCURRENT-RETRY", new BigDecimal("2.0000"));
        Item item = itemRepository.findById(itemId).orElseThrow();
        String requestKey = "transfer-concurrent-" + UUID.randomUUID();
        CreateStockTransferRequest request = transferRequest(
            "Concurrent retry",
            transferLine(item.getSku(), "0.5000")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<StockTransferResponse> results;
        try {
            List<CompletableFuture<StockTransferResponse>> attempts = Stream.generate(() ->
                    CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            start.await(10, TimeUnit.SECONDS);
                            return stockTransferService.createStockTransfer(requestKey, request);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }, executor)
                )
                .limit(2)
                .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = attempts.stream().map(CompletableFuture::join).toList();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(results).extracting(StockTransferResponse::getId).containsOnly(results.getFirst().getId());
        assertThat(results.get(1)).usingRecursiveComparison().isEqualTo(results.getFirst());
        StockTransfer transfer = stockTransferRepository.findByRequestKey(requestKey).orElseThrow();
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.TRANSFER, transfer.getId())).hasSize(2);
        assertThat(itemRepository.findById(itemId).orElseThrow()).satisfies(reloaded -> {
            assertThat(reloaded.getStockStore()).isEqualByComparingTo("1.5000");
            assertThat(reloaded.getStockWarehouse()).isEqualByComparingTo("0.5000");
        });
    }

    @Test
    @Transactional
    void discoversTransferHistoryWithDeterministicPaginationAndEveryFilter() {
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long firstItemId = insertInventoryItem("HISTORY-FIRST-" + unique, new BigDecimal("5.0000"));
        Long secondItemId = insertInventoryItem("HISTORY-SECOND-" + unique, new BigDecimal("5.0000"));
        Instant tiedAt = Instant.parse("2099-01-01T10:00:00Z");
        Instant newestAt = Instant.parse("2099-01-02T10:00:00Z");
        String firstCode = "ST/HISTORY/" + unique + "/A";
        String secondCode = "ST/HISTORY/" + unique + "/B";
        String newestCode = "ST/HISTORY/" + unique + "/C";

        Long firstId = insertHistoryTransfer(
            firstCode, StockLocation.STORE, StockLocation.WAREHOUSE,
            "Two lines", "history-user-a", tiedAt, firstItemId, secondItemId);
        Long secondId = insertHistoryTransfer(
            secondCode, StockLocation.WAREHOUSE, StockLocation.STORE,
            "Same timestamp", "history-user-b", tiedAt, firstItemId);
        Long newestId = insertHistoryTransfer(
            newestCode, StockLocation.STORE, StockLocation.WAREHOUSE,
            "Newest", "history-user-c", newestAt, secondItemId);

        FilterStockTransferRequest historyWindow = FilterStockTransferRequest.builder()
            .createdFrom(Instant.parse("2099-01-01T00:00:00Z"))
            .createdTo(Instant.parse("2099-01-03T00:00:00Z"))
            .build();
        Page<StockTransferSummaryResponse> ordered = stockTransferService.listStockTransfers(
            historyWindow, PageRequest.of(0, 3));
        assertThat(ordered.getContent()).extracting(StockTransferSummaryResponse::getId)
            .containsExactly(newestId, secondId, firstId);
        assertThat(ordered.getContent()).extracting(StockTransferSummaryResponse::getLineCount)
            .containsExactly(1L, 1L, 2L);

        Page<StockTransferSummaryResponse> firstItemFirstPage = stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .itemId(firstItemId)
                .createdFrom(historyWindow.getCreatedFrom())
                .createdTo(historyWindow.getCreatedTo())
                .build(),
            PageRequest.of(0, 1)
        );
        assertThat(firstItemFirstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstItemFirstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstItemFirstPage.getContent()).extracting(StockTransferSummaryResponse::getId)
            .containsExactly(secondId);
        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .itemId(firstItemId)
                .createdFrom(historyWindow.getCreatedFrom())
                .createdTo(historyWindow.getCreatedTo())
                .build(),
            PageRequest.of(1, 1)
        ).getContent()).extracting(StockTransferSummaryResponse::getId)
            .containsExactly(firstId);

        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .code(" st / history / " + unique.toLowerCase() + " / a ")
                .build(), PageRequest.of(0, 10)).getContent())
            .extracting(StockTransferSummaryResponse::getId)
            .containsExactly(firstId);
        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .sourceLocation(StockLocation.WAREHOUSE)
                .createdFrom(historyWindow.getCreatedFrom())
                .createdTo(historyWindow.getCreatedTo())
                .build(), PageRequest.of(0, 10)).getContent())
            .extracting(StockTransferSummaryResponse::getId)
            .containsExactly(secondId);
        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .destinationLocation(StockLocation.WAREHOUSE)
                .createdFrom(historyWindow.getCreatedFrom())
                .createdTo(historyWindow.getCreatedTo())
                .build(), PageRequest.of(0, 10)).getContent())
            .extracting(StockTransferSummaryResponse::getId)
            .containsExactly(newestId, firstId);
        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder().createdFrom(newestAt).build(),
            PageRequest.of(0, 10)).getContent())
            .extracting(StockTransferSummaryResponse::getId)
            .containsExactly(newestId);
        assertThat(stockTransferService.listStockTransfers(
            FilterStockTransferRequest.builder()
                .createdFrom(historyWindow.getCreatedFrom()).createdTo(tiedAt).build(),
            PageRequest.of(0, 10)).getContent())
            .extracting(StockTransferSummaryResponse::getId)
            .containsExactly(secondId, firstId);

        StockTransferResponse detail = stockTransferService.getStockTransferDetails(firstCode);
        assertThat(detail.getCode()).isEqualTo(firstCode);
        assertThat(detail.getCreatedBy()).isEqualTo("history-user-a");
        assertThat(detail.getLines()).hasSize(2);
        String snapshottedName = detail.getLines().getFirst().getItemName();
        jdbcTemplate.update("UPDATE items SET name = ? WHERE id = ?", "Changed later", firstItemId);
        assertThat(stockTransferService.getStockTransferDetails(firstCode).getLines())
            .extracting(line -> line.getItemName())
            .contains(snapshottedName);
        assertThatThrownBy(() -> stockTransferService.getStockTransferDetails("TRF-NOT-FOUND"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Stock transfer not found: TRF-NOT-FOUND");
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

    private boolean indexExists(String indexName) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?)",
            Boolean.class,
            indexName
        );
        return Boolean.TRUE.equals(exists);
    }

    private Long insertInventoryItem(String purpose, BigDecimal storeStock) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO items (
                name, sku, price, stock_quantity, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, 10.0000, ?, ?, 0.0000, 'PIECE', TRUE, TRUE, 1, 0)
            RETURNING id
            """,
            Long.class,
            purpose + " test item",
            purpose + "-" + UUID.randomUUID(),
            storeStock,
            storeStock
        );
    }

    private Long insertStockMovement(
            Long itemId,
            MovementType movementType,
            MovementSourceType sourceType,
            Long sourceId,
            StockLocation location,
            Instant createdAt,
            String referenceNo) {
        BigDecimal before = movementType == MovementType.IN
            ? BigDecimal.ZERO : new BigDecimal("2.0000");
        BigDecimal after = BigDecimal.ONE;
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO stock_movements (
                product_id, movement_type, source_type, source_id, reference_no,
                quantity, created_at, created_by, stock_location, qty_before, qty_after
            ) VALUES (?, ?, ?, ?, ?, 1.0000, ?, 'query-test', ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            itemId,
            movementType.name(),
            sourceType.name(),
            sourceId,
            referenceNo,
            Timestamp.from(createdAt),
            location.name(),
            before,
            after
        );
    }

    private Long insertHistoryTransfer(
            String code,
            StockLocation sourceLocation,
            StockLocation destinationLocation,
            String description,
            String createdBy,
            Instant createdAt,
            Long... itemIds) {
        Long transferId = jdbcTemplate.queryForObject(
            """
            INSERT INTO stock_transfers (
                code, request_key, request_hash, source_location,
                destination_location, description, created_by, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            code,
            "history-" + UUID.randomUUID(),
            "a".repeat(64),
            sourceLocation.name(),
            destinationLocation.name(),
            description,
            createdBy,
            Timestamp.from(createdAt)
        );
        for (Long itemId : itemIds) {
            Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT sku, name, base_unit_of_measure FROM items WHERE id = ?", itemId);
            jdbcTemplate.update(
                """
                INSERT INTO stock_transfer_lines (
                    stock_transfer_id, item_id, item_sku, item_name,
                    unit_of_measure, quantity
                ) VALUES (?, ?, ?, ?, ?, 1.0000)
                """,
                transferId,
                itemId,
                item.get("sku"),
                item.get("name"),
                item.get("base_unit_of_measure")
            );
        }
        return transferId;
    }

    private Sale concurrentSale(long saleId, Item item) {
        return Sale.builder()
            .id(saleId)
            .code("CONCURRENT-SALE-" + saleId)
            .items(List.of(SaleItem.builder()
                .item(item)
                .quantity(new BigDecimal("0.7500"))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();
    }

    private CreateSaleItemRequest saleLine(String sku, String quantity) {
        return CreateSaleItemRequest.builder()
            .itemSku(sku)
            .quantity(new BigDecimal(quantity))
            .stockLocation(StockLocation.STORE)
            .build();
    }

    private CreateStockTransferRequest transferRequest(
            String description, StockTransferLineRequest... lines) {
        return CreateStockTransferRequest.builder()
            .sourceLocation(StockLocation.STORE)
            .destinationLocation(StockLocation.WAREHOUSE)
            .description(description)
            .lines(List.of(lines))
            .build();
    }

    private StockTransferLineRequest transferLine(String sku, String quantity) {
        return StockTransferLineRequest.builder()
            .itemSku(sku)
            .quantity(new BigDecimal(quantity))
            .unitOfMeasure(UnitOfMeasure.PIECE)
            .build();
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
