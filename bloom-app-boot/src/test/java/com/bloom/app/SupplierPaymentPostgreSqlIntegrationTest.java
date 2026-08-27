package com.bloom.app;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptItemRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.request.supplierpayment.VoidSupplierPaymentRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.SupplierPaymentMethod;
import com.bloom.app.domain.enums.SupplierPaymentStatus;
import com.bloom.app.domain.exception.SupplierPaymentConflictException;
import com.bloom.app.domain.exception.SupplierPaymentIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SupplierPaymentRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.CashSessionService;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.SupplierPaymentService;
import com.bloom.app.service.SupplierService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SupplierPaymentPostgreSqlIntegrationTest {
    private static final String EXTERNAL_DATABASE_URL =
        System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired
    private SupplierPaymentService supplierPaymentService;

    @Autowired
    private GoodsReceiptService goodsReceiptService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private CashSessionService cashSessionService;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private CashMovementRepository cashMovementRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ItemRepository itemRepository;

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

    @BeforeEach
    void authenticate() {
        setAuthentication();
    }

    @AfterEach
    void closeOpenSessionAndClearSecurity() {
        cashSessionService.getCurrentSession().ifPresent(session -> {
            var reconciliation = cashSessionService.calculateExpectedCash(session.getId());
            cashSessionService.closeSession(session.getId(), CloseCashSessionRequest.builder()
                .actualClosingCash(reconciliation.getExpectedClosingCash())
                .build());
        });
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void derivesDebtStatusAndSupplierBalanceFromValidPaymentsOnly() {
        Supplier supplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);

        SupplierPaymentResponse first = supplierPaymentService.createPayment(
            receipt.getCode(), key("bank"), payment("40.0000", SupplierPaymentMethod.BANK_TRANSFER));
        GoodsReceiptResponse partial = goodsReceiptService.getGoodsReceiptDetails(receipt.getCode());
        assertThat(partial.getPaidAmount()).isEqualByComparingTo("40.0000");
        assertThat(partial.getOutstandingAmount()).isEqualByComparingTo("60.0000");
        assertThat(partial.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.PARTIALLY_PAID);

        supplierPaymentService.createPayment(
            receipt.getCode(), key("qris"), payment("60.0000", SupplierPaymentMethod.QRIS));
        GoodsReceiptResponse paid = goodsReceiptService.getGoodsReceiptDetails(receipt.getCode());
        assertThat(paid.getPaidAmount()).isEqualByComparingTo("100.0000");
        assertThat(paid.getOutstandingAmount()).isEqualByComparingTo("0.0000");
        assertThat(paid.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.PAID);

        assertThatThrownBy(() -> supplierPaymentService.createPayment(
            receipt.getCode(), key("overpay"), payment("0.0001", SupplierPaymentMethod.QRIS)))
            .isInstanceOf(SupplierPaymentConflictException.class)
            .hasMessageContaining("outstanding amount");

        var balance = supplierService.getOutstandingBalance(supplier.getCode());
        assertThat(balance.getTotalPostedAmount()).isEqualByComparingTo("100.0000");
        assertThat(balance.getValidPayments()).isEqualByComparingTo("100.0000");
        assertThat(balance.getOutstandingAmount()).isEqualByComparingTo("0.0000");

        supplierPaymentService.voidPayment(first.getId(), voidRequest("Bank correction"));
        GoodsReceiptResponse afterVoid = goodsReceiptService.getGoodsReceiptDetails(receipt.getCode());
        assertThat(afterVoid.getPaidAmount()).isEqualByComparingTo("60.0000");
        assertThat(afterVoid.getOutstandingAmount()).isEqualByComparingTo("40.0000");
        assertThat(afterVoid.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.PARTIALLY_PAID);
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getContent())
            .hasSize(2)
            .anySatisfy(history -> {
                assertThat(history.getId()).isEqualTo(first.getId());
                assertThat(history.isVoided()).isTrue();
                assertThat(history.getVoidReason()).isEqualTo("Bank correction");
            });
    }

    @Test
    void optionalInitialPaymentUsesTheSameLedgerAndIsIdempotentWithReceiptPosting() {
        Supplier supplier = supplier();
        CreateSupplierPaymentRequest initial = payment(
            "25.0000", SupplierPaymentMethod.BANK_TRANSFER);
        String receiptKey = key("receipt-initial");
        CreateGoodsReceiptRequest request = receiptRequest(supplier, initial);

        GoodsReceiptResponse created = goodsReceiptService.createGoodsReceipt(
            receiptKey, request);
        GoodsReceiptResponse replay = goodsReceiptService.createGoodsReceipt(
            receiptKey, request);

        assertThat(created.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.PARTIALLY_PAID);
        assertThat(created.getPaidAmount()).isEqualByComparingTo("25.0000");
        assertThat(created.getOutstandingAmount()).isEqualByComparingTo("75.0000");
        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            created.getCode(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void enforcesPaymentIdempotencyOpenDrawerRuleAndDatabaseNoDeleteGuard() {
        Supplier supplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);
        String paymentKey = key("idempotent-payment");
        CreateSupplierPaymentRequest request = payment(
            "10.0000", SupplierPaymentMethod.BANK_TRANSFER);

        SupplierPaymentResponse first = supplierPaymentService.createPayment(
            receipt.getCode(), paymentKey, request);
        SupplierPaymentResponse replay = supplierPaymentService.createPayment(
            receipt.getCode(), paymentKey, request);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> supplierPaymentService.createPayment(
            receipt.getCode(), paymentKey,
            payment("11.0000", SupplierPaymentMethod.BANK_TRANSFER)))
            .isInstanceOf(SupplierPaymentIdempotencyConflictException.class);

        assertThatThrownBy(() -> supplierPaymentService.createPayment(
            receipt.getCode(), key("cash-without-session"),
            payment("10.0000", SupplierPaymentMethod.CASH)))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("open cash session");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "DELETE FROM supplier_payments WHERE id = ?", first.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("supplier payments are immutable");
        assertThat(supplierPaymentRepository.findById(first.getId())).isPresent();
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void concurrentPaymentsSerializeOnReceiptAndCannotCreateSupplierCredit() throws Exception {
        Supplier supplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                String paymentKey = key("concurrent-" + i);
                futures.add(executor.submit(() -> {
                    setAuthentication();
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return supplierPaymentService.createPayment(
                            receipt.getCode(), paymentKey,
                            payment("60.0000", SupplierPaymentMethod.BANK_TRANSFER));
                    } catch (RuntimeException exception) {
                        return exception;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(
                futures.get(0).get(20, TimeUnit.SECONDS),
                futures.get(1).get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(SupplierPaymentResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(SupplierPaymentConflictException.class::isInstance)
                .hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(supplierPaymentRepository.sumValidAmountByReceiptId(receipt.getId()))
            .isEqualByComparingTo("60.0000");
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        GoodsReceiptResponse afterRace = goodsReceiptService
            .getGoodsReceiptDetails(receipt.getCode());
        assertThat(afterRace.getOutstandingAmount()).isEqualByComparingTo("40.0000");
    }

    @Test
    void reconcilesOnlyCashAndCompensatesCashVoidExactlyOnce() {
        Supplier supplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);
        var session = cashSessionService.openSession(OpenCashSessionRequest.builder()
            .openingCash(new BigDecimal("100.0000"))
            .build());

        SupplierPaymentResponse cash = supplierPaymentService.createPayment(
            receipt.getCode(), key("cash"), payment("30.0000", SupplierPaymentMethod.CASH));
        SupplierPaymentResponse bank = supplierPaymentService.createPayment(
            receipt.getCode(), key("bank"), payment("20.0000", SupplierPaymentMethod.BANK_TRANSFER));
        SupplierPaymentResponse qris = supplierPaymentService.createPayment(
            receipt.getCode(), key("qris"), payment("10.0000", SupplierPaymentMethod.QRIS));

        assertThat(cash.getCashSessionId()).isEqualTo(session.getId());
        assertThat(bank.getCashSessionId()).isNull();
        assertThat(qris.getCashSessionId()).isNull();
        assertThat(cashSessionService.calculateExpectedCash(session.getId()).getExpectedClosingCash())
            .isEqualByComparingTo("70.0000");
        assertThat(cashSessionService.getSessionDetails(session.getId()).getExpectedClosingCash())
            .isEqualByComparingTo("70.0000");

        supplierPaymentService.voidPayment(cash.getId(), voidRequest("Duplicate cash payment"));
        supplierPaymentService.voidPayment(cash.getId(), voidRequest("Retry is read-only"));
        supplierPaymentService.voidPayment(bank.getId(), voidRequest("Bank correction"));
        supplierPaymentService.voidPayment(qris.getId(), voidRequest("QRIS correction"));

        assertThat(cashSessionService.calculateExpectedCash(session.getId()).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
        assertThat(cashSessionService.getSessionDetails(session.getId()).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
        var movements = cashMovementRepository.findBySessionId(
            session.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(movements).filteredOn(movement -> movement.getSourceId().equals(cash.getId()))
            .extracting(movement -> movement.getMovementType())
            .containsExactlyInAnyOrder(
                CashMovementType.SUPPLIER_PAYMENT,
                CashMovementType.SUPPLIER_PAYMENT_REVERSAL);
        assertThat(movements).noneSatisfy(movement ->
            assertThat(movement.getSourceId()).isIn(bank.getId(), qris.getId()));
    }

    private GoodsReceiptResponse receipt(
            Supplier supplier, CreateSupplierPaymentRequest initialPayment) {
        return goodsReceiptService.createGoodsReceipt(
            key("receipt"), receiptRequest(supplier, initialPayment));
    }

    private CreateGoodsReceiptRequest receiptRequest(
            Supplier supplier, CreateSupplierPaymentRequest initialPayment) {
        Item item = item();
        return CreateGoodsReceiptRequest.builder()
            .receivedDate(Instant.parse("2026-08-27T07:00:00Z"))
            .supplierCode(supplier.getCode())
            .description("Supplier payment integration receipt")
            .initialPayment(initialPayment)
            .items(List.of(CreateGoodsReceiptItemRequest.builder()
                .itemSku(item.getSku())
                .quantity(new BigDecimal("1.0000"))
                .purchasePrice(new BigDecimal("100.0000"))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();
    }

    private Supplier supplier() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return supplierRepository.saveAndFlush(Supplier.builder()
            .code("PAY-SUP-" + suffix)
            .name("Payment Supplier " + suffix)
            .active(true)
            .build());
    }

    private Item item() {
        String suffix = UUID.randomUUID().toString();
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO items (
                name, sku, price, stock_quantity, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, 100.0000, 0.0000, 0.0000, 0.0000,
                      'PIECE', FALSE, TRUE, 1, 0)
            RETURNING id
            """,
            Long.class,
            "Supplier payment item " + suffix,
            "PAY-ITEM-" + suffix);
        return itemRepository.findById(id).orElseThrow();
    }

    private CreateSupplierPaymentRequest payment(
            String amount, SupplierPaymentMethod method) {
        return CreateSupplierPaymentRequest.builder()
            .amount(new BigDecimal(amount))
            .paymentMethod(method)
            .paidAt(Instant.parse("2026-08-27T08:00:00Z"))
            .reference("REF-" + method)
            .note("Integration payment")
            .build();
    }

    private VoidSupplierPaymentRequest voidRequest(String reason) {
        return VoidSupplierPaymentRequest.builder().reason(reason).build();
    }

    private String key(String purpose) {
        return purpose + "-" + UUID.randomUUID();
    }

    private static void setAuthentication() {
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
