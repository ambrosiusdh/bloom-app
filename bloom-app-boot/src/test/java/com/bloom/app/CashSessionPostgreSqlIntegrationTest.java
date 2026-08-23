package com.bloom.app;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.CashMovementIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.ExpenseRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.CashSessionService;
import com.bloom.app.service.ExpenseService;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CashSessionPostgreSqlIntegrationTest {
    private static final String EXTERNAL_DATABASE_URL =
        System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired
    private CashSessionService cashSessionService;

    @Autowired
    private CashMovementService cashMovementService;

    @Autowired
    private CashSessionRepository cashSessionRepository;

    @Autowired
    private CashMovementRepository cashMovementRepository;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleRepository saleRepository;

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

    @BeforeEach
    void prepareSessionState() {
        closeAnyOpenCashSession();
    }

    @AfterEach
    void clearSecurityAndSessionState() {
        SecurityContextHolder.clearContext();
        closeAnyOpenCashSession();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void serializesConcurrentDoubleOpenSoExactlyOneSessionWins() throws Exception {
        List<Object> outcomes = race(
            () -> cashSessionService.openSession(openRequest("100.0000")),
            () -> cashSessionService.openSession(openRequest("100.0000"))
        );

        assertThat(outcomes).filteredOn(CashSessionResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(CashSessionConflictException.class::isInstance).hasSize(1);
        assertThat(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)).isPresent();
    }

    @Test
    void derivesCashPaginatesLedgerClosesAndFreezesFinancialHistory() {
        authenticateAdmin();
        CashSessionResponse opened = cashSessionService.openSession(openRequest("100.0000"));

        RecordCashMovementCommand sale = movement(
            opened.getId(), CashMovementType.SALE_PAYMENT, 9_001L, "SALE-9001", "50.0000");
        Long saleMovementId = cashMovementService.recordMovement(sale).getId();
        assertThat(cashMovementService.recordMovement(sale).getId()).isEqualTo(saleMovementId);
        cashMovementService.recordMovement(movement(
            opened.getId(), CashMovementType.EXPENSE, 9_002L, "EXPENSE-9002", "20.0000"));

        CashSessionResponse cached = cashSessionService.getSessionDetails(opened.getId());
        assertThat(cached.getExpectedClosingCash()).isEqualByComparingTo("130.0000");
        assertThat(cashSessionService.calculateExpectedCash(opened.getId())
            .getExpectedClosingCash()).isEqualByComparingTo("130.0000");
        assertThat(cashSessionRepository.findById(opened.getId()).orElseThrow().getVersion())
            .isEqualTo(cached.getVersion());

        Page<CashMovementResponse> firstPage = cashSessionService.getSessionMovements(
            opened.getId(), PageRequest.of(0, 1));
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).singleElement()
            .extracting(CashMovementResponse::getReferenceNo)
            .isEqualTo("EXPENSE-9002");

        CashSessionResponse closed = cashSessionService.closeSession(
            opened.getId(), closeRequest("125.0000"));
        assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CLOSED);
        assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("130.0000");
        assertThat(closed.getActualClosingCash()).isEqualByComparingTo("125.0000");
        assertThat(closed.getDifference()).isEqualByComparingTo("-5.0000");

        assertThatThrownBy(() -> cashMovementService.recordMovement(movement(
            opened.getId(), CashMovementType.SALE_PAYMENT, 9_003L, "SALE-9003", "1.0000")))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("is closed");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO cash_movements (
                cash_session_id, movement_type, source_type, source_id, reference_no,
                amount, direction, recorded_at, actor, idempotency_key
            ) VALUES (?, 'SALE_PAYMENT', 'SALE', 9004, 'SALE-9004',
                1.0000, 'IN', CURRENT_TIMESTAMP, 'admin', 'SALE_PAYMENT:9004')
            """, opened.getId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE cash_movements SET amount = 99.0000 WHERE id = ?", saleMovementId))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE cash_sessions SET actual_closing_cash = 999.0000 WHERE id = ?", opened.getId()))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deterministicIdempotencyRejectsChangedPayload() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("10.0000")).getId();
        cashMovementService.recordMovement(movement(
            sessionId, CashMovementType.SALE_PAYMENT, 10_001L, "SALE-10001", "5.0000"));

        assertThatThrownBy(() -> cashMovementService.recordMovement(movement(
            sessionId, CashMovementType.SALE_PAYMENT, 10_001L, "SALE-CHANGED", "6.0000")))
            .isInstanceOf(CashMovementIdempotencyConflictException.class);
        assertThat(cashMovementRepository.findByIdempotencyKey("SALE_PAYMENT:10001")).isPresent();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("15.0000");
    }

    @Test
    void concurrentSameSourceWritesExactlyOneMovement() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("10.0000")).getId();
        SecurityContextHolder.clearContext();
        RecordCashMovementCommand command = movement(
            sessionId, CashMovementType.SALE_PAYMENT, 11_001L, "SALE-11001", "5.0000");

        List<Object> outcomes = race(
            () -> cashMovementService.recordMovement(command),
            () -> cashMovementService.recordMovement(command)
        );

        assertThat(outcomes).allMatch(CashMovementResponse.class::isInstance);
        assertThat(outcomes).extracting(outcome -> ((CashMovementResponse) outcome).getId())
            .containsOnly(((CashMovementResponse) outcomes.getFirst()).getId());
        assertThat(cashMovementRepository.findByIdempotencyKey("SALE_PAYMENT:11001")).isPresent();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("15.0000");
    }

    @Test
    void serializesConcurrentDoubleCloseSoExactlyOneCloseWins() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("40.0000")).getId();
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> cashSessionService.closeSession(sessionId, closeRequest("40.0000")),
            () -> cashSessionService.closeSession(sessionId, closeRequest("40.0000"))
        );

        assertThat(outcomes).filteredOn(CashSessionResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(CashSessionConflictException.class::isInstance).hasSize(1);
        assertThat(cashSessionRepository.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(CashSessionStatus.CLOSED);
    }

    @Test
    void movementRacingCloseIsEitherIncludedOrRejectedNeverPostedLate() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> cashMovementService.recordMovement(movement(
                sessionId, CashMovementType.SALE_PAYMENT, 12_001L, "SALE-12001", "10.0000")),
            () -> cashSessionService.closeSession(sessionId, closeRequest("100.0000"))
        );

        assertThat(outcomes).anyMatch(CashSessionResponse.class::isInstance);
        Object movementOutcome = outcomes.stream()
            .filter(outcome -> !(outcome instanceof CashSessionResponse))
            .findFirst().orElseThrow();
        CashSessionResponse closed = cashSessionService.getSessionDetails(sessionId);
        long movementCount = cashMovementRepository.findByIdempotencyKey("SALE_PAYMENT:12001")
            .stream().count();

        if (movementOutcome instanceof CashMovementResponse) {
            assertThat(movementCount).isEqualTo(1);
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("110.0000");
        } else {
            assertThat(movementOutcome).isInstanceOf(CashSessionConflictException.class);
            assertThat(movementCount).isZero();
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("100.0000");
        }
    }

    @Test
    void legacyClosedSessionReturnsPersistedSnapshotWhileAuditCalculationRemainsDerived() {
        Long sessionId = jdbcTemplate.queryForObject("""
            INSERT INTO cash_sessions (
                opened_by_id, closed_by_id, opening_cash, expected_closing_cash,
                actual_closing_cash, difference, status, opened_at, closed_at, version
            ) VALUES (1, 1, 100.0000, 120.0000, 120.0000, 0.0000,
                'CLOSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            RETURNING id
            """, Long.class);

        CashSessionResponse details = cashSessionService.getSessionDetails(sessionId);
        assertThat(details.getExpectedClosingCash()).isEqualByComparingTo("120.0000");
        assertThat(details.getActualClosingCash()).isEqualByComparingTo("120.0000");
        assertThat(details.getDifference()).isEqualByComparingTo("0.0000");
        assertThat(cashSessionService.calculateExpectedCash(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
    }

    @Test
    void cashCheckoutRecordsTotalNotTenderedCashAndSequentialRetryWritesOnce() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("CASH", "10.0000", "5.0000");
        String checkoutKey = "cash-checkout-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "25.0000", item.sku(), "2.0000");

        SaleResponse first = saleService.createSale(checkoutKey, request);
        SaleResponse retry = saleService.createSale(checkoutKey, request);

        assertThat(retry.getCode()).isEqualTo(first.getCode());
        assertThat(first.getSessionId()).isEqualTo(sessionId);
        assertThat(first.getPaymentType()).isEqualTo(PaymentType.CASH);
        assertThat(first.getPaidAmount()).isEqualByComparingTo("25.0000");
        assertThat(first.getTotalAmount()).isEqualByComparingTo("20.0000");
        assertThat(first.getChangeAmount()).isEqualByComparingTo("5.0000");

        var sale = saleRepository.findByCheckoutIdempotencyKey(checkoutKey).orElseThrow();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE checkout_idempotency_key = ?",
            Long.class, checkoutKey)).isEqualTo(1L);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.SALE, sale.getId())).hasSize(1);
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("3.0000");
        assertThat(cashMovementRepository.findByIdempotencyKey(
            "SALE_PAYMENT:" + sale.getId()).orElseThrow().getAmount())
            .isEqualByComparingTo("20.0000");
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("120.0000");

        CreateSaleRequest changed = checkoutRequest(
            PaymentType.CASH, "30.0000", item.sku(), "2.0000");
        assertThatThrownBy(() -> saleService.createSale(checkoutKey, changed))
            .isInstanceOf(CheckoutIdempotencyConflictException.class);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE checkout_idempotency_key = ?",
            Long.class, checkoutKey)).isEqualTo(1L);
    }

    @Test
    void qrisRequiresExactPaymentAndHasNoPhysicalCashMovement() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("QRIS", "10.0000", "5.0000");

        assertThatThrownBy(() -> saleService.createSale(
            "qris-overpaid-" + UUID.randomUUID(),
            checkoutRequest(PaymentType.QRIS, "11.0000", item.sku(), "1.0000")))
            .isInstanceOf(com.bloom.app.domain.exception.BusinessException.class);
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("5.0000");

        String checkoutKey = "qris-checkout-" + UUID.randomUUID();
        SaleResponse response = saleService.createSale(
            checkoutKey,
            checkoutRequest(PaymentType.QRIS, "10.0000", item.sku(), "1.0000"));

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getPaymentType()).isEqualTo(PaymentType.QRIS);
        assertThat(response.getPaidAmount()).isEqualByComparingTo("10.0000");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("10.0000");
        assertThat(response.getChangeAmount()).isEqualByComparingTo("0.0000");
        var sale = saleRepository.findByCheckoutIdempotencyKey(checkoutKey).orElseThrow();
        assertThat(cashMovementRepository.findByIdempotencyKey(
            "SALE_PAYMENT:" + sale.getId())).isEmpty();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("4.0000");
    }

    @Test
    void checkoutRequiresOpenSessionAndDownstreamCashFailureRollsEverythingBack() {
        ItemFixture item = insertCheckoutItem("ROLLBACK", "10.0000", "5.0000");
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");

        assertThatThrownBy(() -> saleService.createSale(
            "no-session-" + UUID.randomUUID(), request))
            .isInstanceOf(CashSessionConflictException.class);

        authenticateAdmin();
        cashSessionService.openSession(openRequest("100.0000"));
        SecurityContextHolder.clearContext();
        String checkoutKey = "rollback-checkout-" + UUID.randomUUID();
        long cashMovementCountBefore = cashMovementRepository.count();

        assertThatThrownBy(() -> saleService.createSale(checkoutKey, request))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        assertThat(saleRepository.findByCheckoutIdempotencyKey(checkoutKey)).isEmpty();
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("5.0000");
        assertThat(stockMovementRepository.findByProductId(item.id())).isEmpty();
        assertThat(cashMovementRepository.count()).isEqualTo(cashMovementCountBefore);
    }

    @Test
    void concurrentCheckoutRetryReturnsOneSaleOneDeductionAndOneCashMovement() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("CONCURRENT", "10.0000", "5.0000");
        String checkoutKey = "concurrent-checkout-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> saleService.createSale(checkoutKey, request),
            () -> saleService.createSale(checkoutKey, request));

        assertThat(outcomes).allMatch(SaleResponse.class::isInstance);
        assertThat(outcomes).extracting(outcome -> ((SaleResponse) outcome).getCode())
            .containsOnly(((SaleResponse) outcomes.getFirst()).getCode());
        var sale = saleRepository.findByCheckoutIdempotencyKey(checkoutKey).orElseThrow();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE checkout_idempotency_key = ?",
            Long.class, checkoutKey)).isEqualTo(1L);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.SALE, sale.getId())).hasSize(1);
        assertThat(cashMovementRepository.findByIdempotencyKey(
            "SALE_PAYMENT:" + sale.getId())).isPresent();
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("4.0000");
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("110.0000");
    }

    @Test
    void expensePostingAndConcurrentVoidProduceOneOutAndOneReversal() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ExpenseResponse created = expenseService.createExpense(expenseRequest(
            "25.0000", ExpenseCategory.OWNER_WITHDRAWAL, "Owner draw"));

        assertThat(created.getCashSessionId()).isEqualTo(sessionId);
        assertThat(created.isOperationalExpense()).isFalse();
        assertThat(created.isVoided()).isFalse();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("75.0000");
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1L);
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isZero();
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> expenseService.voidExpense(created.getId(), voidRequest("Wrong drawer")),
            () -> expenseService.voidExpense(created.getId(), voidRequest("Retry"))
        );

        assertThat(outcomes).allMatch(ExpenseResponse.class::isInstance);
        ExpenseResponse voided = expenseService.getExpense(created.getId());
        assertThat(voided.isVoided()).isTrue();
        assertThat(voided.getVoidedReason()).isIn("Wrong drawer", "Retry");
        assertThat(voided.getVoidedAt()).isNotNull();
        assertThat(voided.getVoidedBy()).isEqualTo("admin");
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1L);
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isEqualTo(1L);
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
    }

    @Test
    void closedSessionRejectsExpenseVoidAndDatabaseRejectsEditOrDelete() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ExpenseResponse created = expenseService.createExpense(expenseRequest(
            "10.0000", ExpenseCategory.OTHER, "Emergency courier"));

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE expenses SET amount = 11.0000 WHERE id = ?", created.getId()))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "DELETE FROM expenses WHERE id = ?", created.getId()))
            .isInstanceOf(DataAccessException.class);

        cashSessionService.closeSession(sessionId, closeRequest("90.0000"));
        assertThatThrownBy(() -> expenseService.voidExpense(
            created.getId(), voidRequest("Discovered after close")))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("closed");
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isZero();
        assertThat(expenseRepository.findById(created.getId()).orElseThrow().isVoided()).isFalse();
    }

    @Test
    void expenseValidationAndLedgerFailureLeaveNoPartialExpense() {
        assertThatThrownBy(() -> expenseService.createExpense(expenseRequest(
            "1.0000", ExpenseCategory.CHARITY, null)))
            .isInstanceOf(CashSessionConflictException.class);

        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        assertThatThrownBy(() -> expenseService.createExpense(expenseRequest(
            "0.0000", ExpenseCategory.CHARITY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
        assertThatThrownBy(() -> expenseService.createExpense(expenseRequest(
            "1.0000", ExpenseCategory.OTHER, "  ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required for OTHER");

        Long consumedId = jdbcTemplate.queryForObject(
            "SELECT nextval('expenses_id_seq')", Long.class);
        long conflictingExpenseId = consumedId + 1;
        jdbcTemplate.update("""
            INSERT INTO cash_movements (
                cash_session_id, movement_type, source_type, source_id, reference_no,
                amount, direction, recorded_at, actor, idempotency_key
            ) VALUES (?, 'EXPENSE', 'EXPENSE', ?, 'CONFLICTING-EXPENSE',
                99.0000, 'OUT', CURRENT_TIMESTAMP, 'admin', ?)
            """, sessionId, conflictingExpenseId, "EXPENSE:" + conflictingExpenseId);

        long expenseCountBefore = expenseRepository.count();
        assertThatThrownBy(() -> expenseService.createExpense(expenseRequest(
            "5.0000", ExpenseCategory.STORE_OPERATIONAL, "Cleaning supplies")))
            .isInstanceOf(CashMovementIdempotencyConflictException.class);
        assertThat(expenseRepository.count()).isEqualTo(expenseCountBefore);
        assertThat(expenseRepository.findById(conflictingExpenseId)).isEmpty();
    }

    private List<Object> race(Supplier<?> first, Supplier<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Object>> attempts = List.of(first, second).stream()
                .map(operation -> CompletableFuture.supplyAsync(() -> {
                    authenticateAdmin();
                    ready.countDown();
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        return operation.get();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return new IllegalStateException(exception);
                    } catch (RuntimeException exception) {
                        return exception;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }, executor))
                .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return attempts.stream().map(CompletableFuture::join).toList();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void authenticateAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "ignored", List.of()));
    }

    private OpenCashSessionRequest openRequest(String amount) {
        return OpenCashSessionRequest.builder().openingCash(new BigDecimal(amount)).build();
    }

    private CloseCashSessionRequest closeRequest(String amount) {
        return CloseCashSessionRequest.builder()
            .actualClosingCash(new BigDecimal(amount)).build();
    }

    private RecordCashMovementCommand movement(
            Long sessionId,
            CashMovementType type,
            Long sourceId,
            String reference,
            String amount) {
        return new RecordCashMovementCommand(
            sessionId, type, sourceId, reference, new BigDecimal(amount));
    }

    private CreateExpenseRequest expenseRequest(
            String amount, ExpenseCategory category, String description) {
        return CreateExpenseRequest.builder()
            .amount(new BigDecimal(amount))
            .category(category)
            .description(description)
            .build();
    }

    private VoidExpenseRequest voidRequest(String reason) {
        return VoidExpenseRequest.builder().reason(reason).build();
    }

    private long movementCount(Long expenseId, String movementType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM cash_movements
            WHERE source_type = 'EXPENSE' AND source_id = ? AND movement_type = ?
            """, Long.class, expenseId, movementType);
    }

    private ItemFixture insertCheckoutItem(String purpose, String price, String storeStock) {
        String sku = "CHECKOUT-" + purpose + "-" + UUID.randomUUID();
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO items (
                name, sku, price, stock_quantity, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, ?, ?, ?, 0.0000, 'PIECE', FALSE, TRUE, 1, 0)
            RETURNING id
            """, Long.class, purpose + " checkout item", sku,
            new BigDecimal(price), new BigDecimal(storeStock), new BigDecimal(storeStock));
        return new ItemFixture(id, sku);
    }

    private CreateSaleRequest checkoutRequest(
            PaymentType paymentType,
            String paidAmount,
            String itemSku,
            String quantity) {
        return CreateSaleRequest.builder()
            .discountAmount(BigDecimal.ZERO)
            .paidAmount(new BigDecimal(paidAmount))
            .paymentType(paymentType)
            .saleItemList(List.of(CreateSaleItemRequest.builder()
                .itemSku(itemSku)
                .quantity(new BigDecimal(quantity))
                .stockLocation(StockLocation.STORE)
                .build()))
            .build();
    }

    private record ItemFixture(Long id, String sku) {
    }

    private void closeAnyOpenCashSession() {
        jdbcTemplate.update("""
            UPDATE cash_sessions
            SET status = 'CLOSED',
                actual_closing_cash = expected_closing_cash,
                difference = 0.0000,
                closed_at = CURRENT_TIMESTAMP,
                closed_by_id = opened_by_id,
                version = version + 1
            WHERE status = 'OPEN'
            """);
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
