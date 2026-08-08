package com.bloom.app;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.StockAdjustmentItemRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.model.DocumentCounter;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.persistence.repository.DocumentCounterRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.persistence.repository.StockAdjustmentRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.StockAdjustmentService;
import com.bloom.app.service.StockMovementService;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
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
    private SaleService saleService;

    @Autowired
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");

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
        assertThat(constraintExists(
            "stock_movements",
            "chk_stock_movements_balance_equation"
        )).isTrue();
        assertThat(indexExists("uq_stock_movements_sale_item_location")).isTrue();

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

    @Test
    void rollsBackBalanceAndMovementWhenLegacyAuditWriteFails() {
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
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(itemRepository.findById(itemId).orElseThrow().getStockStore())
            .isEqualByComparingTo("1.0000");
        assertThat(stockMovementRepository.findByProductId(itemId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_audit_logs WHERE item_id = ?", Long.class, itemId))
            .isZero();
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
