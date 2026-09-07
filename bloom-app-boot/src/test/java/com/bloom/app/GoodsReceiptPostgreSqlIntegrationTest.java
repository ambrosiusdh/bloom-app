package com.bloom.app;

import com.bloom.app.api.dto.request.goodsreceipt.CancelGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptItemRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.SupplierPaymentMethod;
import com.bloom.app.domain.enums.SupplierPaymentStatus;
import com.bloom.app.domain.exception.GoodsReceiptConflictException;
import com.bloom.app.domain.exception.GoodsReceiptIdempotencyConflictException;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.SupplierPaymentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GoodsReceiptPostgreSqlIntegrationTest {
    private static final String EXTERNAL_DATABASE_URL =
        System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired
    private GoodsReceiptService goodsReceiptService;

    @Autowired
    private SupplierPaymentService supplierPaymentService;

    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

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

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void createsAndPostsWithServerCalculatedSnapshotsAndIdempotentReplay() {
        authenticate();
        Supplier supplier = supplier(true);
        Item item = fractionalItem("RECEIPT-CALC", "0.0000", "0.0000");
        CreateGoodsReceiptRequest request = request(supplier.getCode(), List.of(
            line(item.getSku(), "1.2345", "2.3456", StockLocation.STORE),
            line(item.getSku(), "2.0000", "1.1111", StockLocation.WAREHOUSE)
        ));
        String key = "goods-receipt-" + UUID.randomUUID();
        long receiptCountBefore = goodsReceiptRepository.count();

        GoodsReceiptResponse created = goodsReceiptService.createGoodsReceipt(key, request);
        BigDecimal firstLineTotal = new BigDecimal("1.2345")
            .multiply(new BigDecimal("2.3456"))
            .setScale(4, RoundingMode.HALF_UP);
        BigDecimal secondLineTotal = new BigDecimal("2.0000")
            .multiply(new BigDecimal("1.1111"))
            .setScale(4, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = firstLineTotal.add(secondLineTotal)
            .setScale(4, RoundingMode.HALF_UP);

        assertThat(created.getStatus()).isEqualTo(GoodsReceiptStatus.POSTED);
        assertThat(created.getSupplierId()).isEqualTo(supplier.getId());
        assertThat(created.getSupplierCode()).isEqualTo(supplier.getCode());
        assertThat(created.getSupplierName()).isEqualTo(supplier.getName());
        assertThat(created.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(created.getPaidAmount()).isEqualByComparingTo("0.0000");
        assertThat(created.getOutstandingAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(created.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.UNPAID);
        assertThat(created.getItems()).extracting(line -> line.getPurchasePrice())
            .containsExactly(new BigDecimal("2.3456"), new BigDecimal("1.1111"));
        assertThat(created.getItems()).extracting(line -> line.getLineTotal())
            .containsExactly(firstLineTotal, secondLineTotal);
        assertThat(created.getItems()).extracting(line -> line.getBaseUnitOfMeasure())
            .containsOnly(UnitOfMeasure.METER);
        assertThat(created.getItems()).extracting(line -> line.getStockLocation())
            .containsExactly(StockLocation.STORE, StockLocation.WAREHOUSE);

        GoodsReceipt saved = goodsReceiptRepository.findDetailsByCode(created.getCode())
            .orElseThrow();
        assertThat(saved.getSupplier().getId()).isEqualTo(supplier.getId());
        assertThat(saved.getSupplierNameSnapshot()).isEqualTo(supplier.getName());
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.GOODS_RECEIPT, saved.getId())).hasSize(2);
        assertThat(itemRepository.findById(item.getId()).orElseThrow()).satisfies(postedItem -> {
            assertThat(postedItem.getStockStore()).isEqualByComparingTo("1.2345");
            assertThat(postedItem.getStockWarehouse()).isEqualByComparingTo("2.0000");
        });

        String originalSupplierName = supplier.getName();
        supplier.setName("Renamed after posting");
        supplierRepository.saveAndFlush(supplier);
        GoodsReceiptResponse replay = goodsReceiptService.createGoodsReceipt(key, request);
        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(replay.getSupplierName()).isEqualTo(originalSupplierName);
        assertThat(goodsReceiptRepository.count()).isEqualTo(receiptCountBefore + 1);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.GOODS_RECEIPT, saved.getId())).hasSize(2);

        CreateGoodsReceiptRequest changed = request(supplier.getCode(), List.of(
            line(item.getSku(), "1.2345", "9.9999", StockLocation.STORE)
        ));
        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(key, changed))
            .isInstanceOf(GoodsReceiptIdempotencyConflictException.class);
    }

    @Test
    void cancellationPostsCompensatingMovementExactlyOnceAndRejectsPaidReceipt() {
        authenticate();
        Supplier supplier = supplier(true);
        Item item = fractionalItem("RECEIPT-CANCEL", "5.0000", "0.0000");
        GoodsReceiptResponse created = goodsReceiptService.createGoodsReceipt(
            "goods-receipt-" + UUID.randomUUID(),
            request(supplier.getCode(), List.of(
                line(item.getSku(), "1.2500", "4.0000", StockLocation.STORE))));

        GoodsReceiptResponse cancelled = goodsReceiptService.cancelGoodsReceipt(
            created.getCode(), CancelGoodsReceiptRequest.builder().reason("Supplier return").build());
        assertThat(cancelled.getStatus()).isEqualTo(GoodsReceiptStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Supplier return");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancelledBy()).isEqualTo("admin");
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStockStore())
            .isEqualByComparingTo("5.0000");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.GOODS_RECEIPT_CANCELLATION, created.getId())).hasSize(1);

        GoodsReceiptResponse replayedCancellation = goodsReceiptService.cancelGoodsReceipt(
            created.getCode(), CancelGoodsReceiptRequest.builder().reason("Ignored retry text").build());
        assertThat(replayedCancellation.getCancelledAt()).isEqualTo(cancelled.getCancelledAt());
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.GOODS_RECEIPT_CANCELLATION, created.getId())).hasSize(1);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStockStore())
            .isEqualByComparingTo("5.0000");

        GoodsReceiptResponse paid = goodsReceiptService.createGoodsReceipt(
            "goods-receipt-" + UUID.randomUUID(),
            request(supplier.getCode(), List.of(
                line(item.getSku(), "1.0000", "4.0000", StockLocation.WAREHOUSE))));
        supplierPaymentService.createPayment(
            paid.getCode(),
            "payment-" + UUID.randomUUID(),
            CreateSupplierPaymentRequest.builder()
                .amount(new BigDecimal("1.0000"))
                .paymentMethod(SupplierPaymentMethod.BANK_TRANSFER)
                .paidAt(Instant.parse("2026-08-25T11:00:00Z"))
                .reference("BANK-REF")
                .build());

        assertThatThrownBy(() -> goodsReceiptService.cancelGoodsReceipt(
            paid.getCode(), CancelGoodsReceiptRequest.builder().reason("Not allowed").build()))
            .isInstanceOf(GoodsReceiptConflictException.class)
            .hasMessageContaining("active payments");
        assertThat(goodsReceiptRepository.findDetailsByCode(paid.getCode()).orElseThrow().getStatus())
            .isEqualTo(GoodsReceiptStatus.POSTED);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.GOODS_RECEIPT_CANCELLATION, paid.getId())).isEmpty();
    }

    @Test
    void rejectsInactiveSupplierAndInvalidFinancialOrQuantityInputBeforePosting() {
        authenticate();
        Supplier inactiveSupplier = supplier(false);
        Item item = fractionalItem("RECEIPT-VALIDATION", "0.0000", "0.0000");
        CreateGoodsReceiptRequest validShape = request(inactiveSupplier.getCode(), List.of(
            line(item.getSku(), "1.0000", "1.0000", StockLocation.STORE)));

        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(
            "goods-receipt-" + UUID.randomUUID(), validShape))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Supplier must be active");

        Supplier activeSupplier = supplier(true);
        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(
            "goods-receipt-" + UUID.randomUUID(),
            request(activeSupplier.getCode(), List.of(
                line(item.getSku(), "1.0000", "0.0000", StockLocation.STORE)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Purchase price must be positive");
        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(
            "goods-receipt-" + UUID.randomUUID(),
            request(activeSupplier.getCode(), List.of(
                line(item.getSku(), "0.0000", "1.0000", StockLocation.STORE)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
        assertThat(stockMovementRepository.findByProductId(item.getId())).isEmpty();
    }

    @Test
    void rejectsReceiptCodesBeyondTheDatabaseLimitBeforeLookupOrCancellation() {
        assertThatThrownBy(() -> goodsReceiptService.getGoodsReceiptDetails("G".repeat(101)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Goods receipt code must not exceed 100 characters");
        assertThatThrownBy(() -> goodsReceiptService.cancelGoodsReceipt(
            "G".repeat(101), CancelGoodsReceiptRequest.builder().reason("Invalid code").build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Goods receipt code must not exceed 100 characters");
    }

    private Supplier supplier(boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return supplierRepository.saveAndFlush(Supplier.builder()
            .code("GR-SUP-" + suffix)
            .name("Receipt Supplier " + suffix)
            .active(active)
            .build());
    }

    private Item fractionalItem(
            String purpose, String stockStore, String stockWarehouse) {
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO items (
                name, sku, price, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, 10.0000, ?, ?, 'METER', TRUE, TRUE, 1, 0)
            RETURNING id
            """,
            Long.class,
            purpose + " test item",
            purpose + "-" + UUID.randomUUID(),
            new BigDecimal(stockStore),
            new BigDecimal(stockWarehouse));
        return itemRepository.findById(id).orElseThrow();
    }

    private CreateGoodsReceiptRequest request(
            String supplierCode, List<CreateGoodsReceiptItemRequest> lines) {
        return CreateGoodsReceiptRequest.builder()
            .receivedDate(Instant.parse("2026-08-25T10:00:00Z"))
            .supplierCode(supplierCode)
            .description(" Release 1 receipt ")
            .items(lines)
            .build();
    }

    private CreateGoodsReceiptItemRequest line(
            String sku, String quantity, String purchasePrice, StockLocation location) {
        return CreateGoodsReceiptItemRequest.builder()
            .itemSku(sku)
            .quantity(new BigDecimal(quantity))
            .purchasePrice(new BigDecimal(purchasePrice))
            .stockLocation(location)
            .build();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "ignored", List.of()));
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
