package com.bloom.app;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CancelGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptItemRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.request.supplier.UpdateSupplierRequest;
import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.request.supplierpayment.VoidSupplierPaymentRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.SupplierPaymentMethod;
import com.bloom.app.domain.enums.SupplierPaymentStatus;
import com.bloom.app.domain.exception.SupplierPaymentConflictException;
import com.bloom.app.domain.exception.SupplierPaymentIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.ItemCategoryRepository;
import com.bloom.app.persistence.repository.SupplierPaymentRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.CashSessionService;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.SaleService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
    private SaleService saleService;

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
    private ItemCategoryRepository itemCategoryRepository;

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
        assertThat(balance.getPaidAmount()).isEqualByComparingTo("100.0000");
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

    @Test
    void cancelledReceiptHasNoOutstandingPayable() {
        Supplier supplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);

        GoodsReceiptResponse cancelled = goodsReceiptService.cancelGoodsReceipt(
            receipt.getCode(),
            CancelGoodsReceiptRequest.builder().reason("Posting correction").build()
        );

        assertThat(cancelled.getStatus()).isEqualTo(GoodsReceiptStatus.CANCELLED);
        assertThat(cancelled.getPaidAmount()).isEqualByComparingTo("0.0000");
        assertThat(cancelled.getOutstandingAmount()).isEqualByComparingTo("0.0000");
        assertThat(cancelled.getPaymentStatus()).isEqualTo(SupplierPaymentStatus.UNPAID);
        var supplierBalance = supplierService.getOutstandingBalance(supplier.getCode());
        assertThat(supplierBalance.getTotalPostedAmount()).isEqualByComparingTo("0.0000");
        assertThat(supplierBalance.getPaidAmount()).isEqualByComparingTo("0.0000");
        assertThat(supplierBalance.getOutstandingAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void concurrentIdenticalIdempotencyRequestsReturnOnePayment() throws Exception {
        GoodsReceiptResponse receipt = receipt(supplier(), null);
        String paymentKey = key("same-key-same-request");
        CreateSupplierPaymentRequest request = payment(
            "40.0000", SupplierPaymentMethod.BANK_TRANSFER);

        List<Object> results = runConcurrentPayments(
            receipt.getCode(), paymentKey, List.of(request, request));

        assertThat(results).allMatch(SupplierPaymentResponse.class::isInstance);
        Long paymentId = ((SupplierPaymentResponse) results.get(0)).getId();
        assertThat(results)
            .extracting(result -> ((SupplierPaymentResponse) result).getId())
            .containsOnly(paymentId);
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentRequestsWithSameKeyProduceOneConflict() throws Exception {
        GoodsReceiptResponse receipt = receipt(supplier(), null);
        String paymentKey = key("same-key-different-request");

        List<Object> results = runConcurrentPayments(
            receipt.getCode(),
            paymentKey,
            List.of(
                payment("40.0000", SupplierPaymentMethod.BANK_TRANSFER),
                payment("50.0000", SupplierPaymentMethod.BANK_TRANSFER)
            )
        );

        assertThat(results).filteredOn(SupplierPaymentResponse.class::isInstance).hasSize(1);
        assertThat(results)
            .filteredOn(SupplierPaymentIdempotencyConflictException.class::isInstance)
            .hasSize(1);
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void failedInitialPaymentRollsBackReceiptStockAndPayment() {
        Supplier supplier = supplier();
        String noSessionKey = key("initial-cash-no-session");
        CreateGoodsReceiptRequest noSessionRequest = receiptRequest(
            supplier, payment("10.0000", SupplierPaymentMethod.CASH));

        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(
            noSessionKey, noSessionRequest))
            .isInstanceOf(CashSessionConflictException.class);
        assertPostingRolledBack(noSessionKey, noSessionRequest.getItems().get(0).getItemSku());

        String overpaymentKey = key("initial-overpayment");
        CreateGoodsReceiptRequest overpaymentRequest = receiptRequest(
            supplier, payment("100.0001", SupplierPaymentMethod.BANK_TRANSFER));

        assertThatThrownBy(() -> goodsReceiptService.createGoodsReceipt(
            overpaymentKey, overpaymentRequest))
            .isInstanceOf(SupplierPaymentConflictException.class);
        assertPostingRolledBack(
            overpaymentKey, overpaymentRequest.getItems().get(0).getItemSku());
    }

    @Test
    void closedSessionRejectsCashVoidAndPreservesOriginalLedger() {
        GoodsReceiptResponse receipt = receipt(supplier(), null);
        var session = cashSessionService.openSession(OpenCashSessionRequest.builder()
            .openingCash(new BigDecimal("100.0000"))
            .build());
        SupplierPaymentResponse cash = supplierPaymentService.createPayment(
            receipt.getCode(), key("closed-session-cash"),
            payment("30.0000", SupplierPaymentMethod.CASH));
        cashSessionService.closeSession(session.getId(), CloseCashSessionRequest.builder()
            .actualClosingCash(new BigDecimal("70.0000"))
            .build());

        assertThatThrownBy(() -> supplierPaymentService.voidPayment(
            cash.getId(), voidRequest("Post-close correction attempt")))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("original cash session is closed");

        SupplierPaymentResponse unchanged = supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getContent().get(0);
        assertThat(unchanged.isVoided()).isFalse();
        assertThat(cashMovementRepository.findBySessionId(
            session.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void cashPaymentTimeMustBelongToTheOpenSession() {
        GoodsReceiptResponse receipt = receipt(supplier(), null);
        var session = cashSessionService.openSession(OpenCashSessionRequest.builder()
            .openingCash(new BigDecimal("100.0000"))
            .build());
        CreateSupplierPaymentRequest request = payment(
            "10.0000", SupplierPaymentMethod.CASH);
        request.setPaidAt(session.getOpenedAt().minusSeconds(1));

        assertThatThrownBy(() -> supplierPaymentService.createPayment(
            receipt.getCode(), key("predates-session"), request))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("cannot predate");
        assertThat(supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(cashMovementRepository.findBySessionId(
            session.getId(), PageRequest.of(0, 10)).getTotalElements()).isZero();
    }

    @Test
    void paymentHistoryRetainsReceiptSupplierNameSnapshot() {
        Supplier supplier = supplier();
        String originalName = supplier.getName();
        GoodsReceiptResponse receipt = receipt(supplier, null);
        supplierPaymentService.createPayment(
            receipt.getCode(), key("supplier-rename"),
            payment("10.0000", SupplierPaymentMethod.BANK_TRANSFER));

        supplierService.updateSupplier(supplier.getCode(), UpdateSupplierRequest.builder()
            .name("Renamed supplier")
            .build());

        SupplierPaymentResponse history = supplierPaymentService.getReceiptPaymentHistory(
            receipt.getCode(), PageRequest.of(0, 10)).getContent().get(0);
        assertThat(history.getSupplierName()).isEqualTo(originalName);
    }

    @Test
    void databaseTriggersRejectInvalidPaymentFactsAndPartialVoid() {
        Supplier supplier = supplier();
        Supplier otherSupplier = supplier();
        GoodsReceiptResponse receipt = receipt(supplier, null);
        SupplierPaymentResponse payment = supplierPaymentService.createPayment(
            receipt.getCode(), key("trigger-base"),
            payment("10.0000", SupplierPaymentMethod.BANK_TRANSFER));

        assertThatThrownBy(() -> insertDirectPayment(
            receipt.getId(), otherSupplier.getId(), "1.0000", key("wrong-supplier")))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("supplier payment supplier does not match");
        assertThatThrownBy(() -> insertDirectPayment(
            receipt.getId(), supplier.getId(), "90.0001", key("direct-overpay")))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("would overpay");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE supplier_payments SET reference = ? WHERE id = ?",
            "edited", payment.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("facts are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE supplier_payments SET is_voided = TRUE WHERE id = ?", payment.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("complete first-time void");
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            UPDATE goods_receipts
            SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = 'admin',
                cancellation_reason = 'Direct cancellation'
            WHERE id = ?
            """,
            Timestamp.from(Instant.now()),
            receipt.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("active supplier payments");

        GoodsReceiptResponse cancelled = receipt(supplier, null);
        goodsReceiptService.cancelGoodsReceipt(cancelled.getCode(),
            CancelGoodsReceiptRequest.builder().reason("Cancel for trigger test").build());
        assertThatThrownBy(() -> insertDirectPayment(
            cancelled.getId(), supplier.getId(), "1.0000", key("cancelled-receipt")))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("is not posted");
    }

    @Test
    void cashSaleAndInitialCashReceiptUseCompatibleLockOrder() throws Exception {
        Supplier supplier = supplier();
        Item sharedItem = item();
        jdbcTemplate.update(
            "UPDATE items SET stock_store = 5.0000 WHERE id = ?", sharedItem.getId());
        var session = cashSessionService.openSession(OpenCashSessionRequest.builder()
            .openingCash(new BigDecimal("1000.0000"))
            .build());
        CreateGoodsReceiptRequest receiptRequest = CreateGoodsReceiptRequest.builder()
            .receivedDate(Instant.now())
            .supplierCode(supplier.getCode())
            .initialPayment(payment("100.0000", SupplierPaymentMethod.CASH))
            .items(List.of(CreateGoodsReceiptItemRequest.builder()
                .itemSku(sharedItem.getSku())
                .quantity(new BigDecimal("1.0000"))
                .purchasePrice(new BigDecimal("100.0000"))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();
        CreateSaleRequest saleRequest = CreateSaleRequest.builder()
            .discountAmount(BigDecimal.ZERO.setScale(4))
            .paidAmount(new BigDecimal("100.0000"))
            .paymentType(PaymentType.CASH)
            .saleItemList(List.of(CreateSaleItemRequest.builder()
                .itemSku(sharedItem.getSku())
                .quantity(new BigDecimal("1.0000"))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> receiptFuture = executor.submit(() -> runConcurrentOperation(
                ready, start, () -> goodsReceiptService.createGoodsReceipt(
                    key("cash-lock-receipt"), receiptRequest)));
            Future<Object> saleFuture = executor.submit(() -> runConcurrentOperation(
                ready, start, () -> saleService.createSale(key("cash-lock-sale"), saleRequest)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(receiptFuture.get(20, TimeUnit.SECONDS))
                .isInstanceOf(GoodsReceiptResponse.class);
            assertThat(saleFuture.get(20, TimeUnit.SECONDS)).isInstanceOf(SaleResponse.class);
        } finally {
            executor.shutdownNow();
        }
        assertThat(cashSessionService.calculateExpectedCash(session.getId()).getExpectedClosingCash())
            .isEqualByComparingTo("1000.0000");
    }

    private List<Object> runConcurrentPayments(
            String receiptCode,
            String idempotencyKey,
            List<CreateSupplierPaymentRequest> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (CreateSupplierPaymentRequest request : requests) {
                futures.add(executor.submit(() -> runConcurrentOperation(
                    ready,
                    start,
                    () -> supplierPaymentService.createPayment(
                        receiptCode, idempotencyKey, request)
                )));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Object runConcurrentOperation(
            CountDownLatch ready, CountDownLatch start, Callable<Object> operation) {
        setAuthentication();
        ready.countDown();
        try {
            start.await(10, TimeUnit.SECONDS);
            return operation.call();
        } catch (Exception exception) {
            return exception;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertPostingRolledBack(String receiptKey, String itemSku) {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goods_receipts WHERE create_idempotency_key = ?",
            Long.class,
            receiptKey)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_movements movement
            JOIN items item ON item.id = movement.product_id
            WHERE item.sku = ?
            """,
            Long.class,
            itemSku)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT stock_store FROM items WHERE sku = ?",
            BigDecimal.class,
            itemSku)).isEqualByComparingTo("0.0000");
    }

    private void insertDirectPayment(
            Long receiptId, Long supplierId, String amount, String idempotencyKey) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO supplier_payments (
                goods_receipt_id, supplier_id, cash_session_id, amount,
                payment_method, paid_at, reference, note, actor, is_voided,
                idempotency_key, request_hash, created_at, version
            ) VALUES (?, ?, NULL, ?, 'BANK_TRANSFER', ?, NULL, NULL, 'admin', FALSE,
                      ?, REPEAT('a', 64), ?, 0)
            """,
            receiptId,
            supplierId,
            new BigDecimal(amount),
            Timestamp.from(now),
            idempotencyKey,
            Timestamp.from(now)
        );
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
        ItemCategory category = itemCategoryRepository.saveAndFlush(ItemCategory.builder()
            .code("PAY-CAT-" + suffix)
            .name("Supplier payment category " + suffix)
            .active(true)
            .build());
        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO items (
                name, sku, price, stock_quantity, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, 100.0000, 0.0000, 0.0000, 0.0000,
                      'PIECE', FALSE, TRUE, ?, 0)
            RETURNING id
            """,
            Long.class,
            "Supplier payment item " + suffix,
            "PAY-ITEM-" + suffix,
            category.getId());
        return itemRepository.findById(id).orElseThrow();
    }

    private CreateSupplierPaymentRequest payment(
            String amount, SupplierPaymentMethod method) {
        return CreateSupplierPaymentRequest.builder()
            .amount(new BigDecimal(amount))
            .paymentMethod(method)
            .paidAt(Instant.now())
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
