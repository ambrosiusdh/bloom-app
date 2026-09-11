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
import com.bloom.app.api.dto.response.sale.SaleCheckoutStatusResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.enums.ExpenseVoidBlockReason;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.CashMovementIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.domain.exception.ExpenseIdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.hibernate.SessionFactory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
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
    void currentSessionLookupReturnsZeroOrOneWhileSpecificLookupStillRequiresExistingId() {
        assertThat(cashSessionService.getCurrentSession()).isEmpty();

        authenticateAdmin();
        CashSessionResponse opened = cashSessionService.openSession(openRequest("100.0000"));

        assertThat(cashSessionService.getCurrentSession())
            .get()
            .satisfies(current -> {
                assertThat(current.getId()).isEqualTo(opened.getId());
                assertThat(current.getOpeningCash()).isEqualByComparingTo("100.0000");
                assertThat(current.getExpectedClosingCash()).isEqualByComparingTo("100.0000");
                assertThat(current.getOpenedAt()).isNotNull();
                assertThat(current.getOpenedBy()).isEqualTo("admin");
                assertThat(current.getStatus()).isEqualTo(CashSessionStatus.OPEN);
                assertThat(current.getVersion()).isEqualTo(opened.getVersion());
            });
        assertThatThrownBy(() -> cashSessionService.getSessionDetails(Long.MAX_VALUE))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Cash session not found: " + Long.MAX_VALUE);
    }

    @Test
    void historyListsAndFiltersAuthoritativeSnapshotsWithoutActorNPlusOneReads() {
        Long adminId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'admin'", Long.class);
        Long olderClosedId = jdbcTemplate.queryForObject("""
            INSERT INTO cash_sessions (
                opened_by_id, closed_by_id, opening_cash, expected_closing_cash,
                actual_closing_cash, difference, status, opened_at, closed_at, version
            ) VALUES (?, ?, 100.0000, 130.0000, 125.0000, -5.0000,
                'CLOSED', ?, ?, 0)
            RETURNING id
            """, Long.class, adminId, adminId,
                java.sql.Timestamp.from(java.time.Instant.parse("2126-08-20T01:02:03Z")),
                java.sql.Timestamp.from(java.time.Instant.parse("2126-08-20T09:10:11Z")));
        Long newerClosedId = jdbcTemplate.queryForObject("""
            INSERT INTO cash_sessions (
                opened_by_id, closed_by_id, opening_cash, expected_closing_cash,
                actual_closing_cash, difference, status, opened_at, closed_at, version
            ) VALUES (?, ?, 200.0000, 240.0000, 241.0000, 1.0000,
                'CLOSED', ?, ?, 0)
            RETURNING id
            """, Long.class, adminId, adminId,
                java.sql.Timestamp.from(java.time.Instant.parse("2126-08-20T01:02:03Z")),
                java.sql.Timestamp.from(java.time.Instant.parse("2126-08-20T10:10:11Z")));
        Long openId = jdbcTemplate.queryForObject("""
            INSERT INTO cash_sessions (
                opened_by_id, opening_cash, expected_closing_cash,
                actual_closing_cash, difference, status, opened_at, closed_at,
                closed_by_id, version
            ) VALUES (?, 75.0000, 75.0000, NULL, NULL, 'OPEN',
                ?, NULL, NULL, 0)
            RETURNING id
            """, Long.class, adminId,
                java.sql.Timestamp.from(java.time.Instant.parse("2126-08-21T01:02:03Z")));

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();
        Page<CashSessionResponse> unfiltered = cashSessionService.getSessionHistory(
            null, PageRequest.of(0, 3));

        assertThat(unfiltered.getContent()).extracting(CashSessionResponse::getId)
            .containsExactly(openId, newerClosedId, olderClosedId);
        assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(2);

        Page<CashSessionResponse> openOnly = cashSessionService.getSessionHistory(
            CashSessionStatus.OPEN, PageRequest.of(0, 10));
        assertThat(openOnly.getContent()).singleElement().satisfies(open -> {
            assertThat(open.getId()).isEqualTo(openId);
            assertThat(open.getOpeningCash()).isEqualByComparingTo("75.0000");
            assertThat(open.getExpectedClosingCash()).isEqualByComparingTo("75.0000");
            assertThat(open.getActualClosingCash()).isNull();
            assertThat(open.getDifference()).isNull();
            assertThat(open.getStatus()).isEqualTo(CashSessionStatus.OPEN);
            assertThat(open.getOpenedAt()).isEqualTo(
                java.time.Instant.parse("2126-08-21T01:02:03Z"));
            assertThat(open.getOpenedBy()).isEqualTo("admin");
            assertThat(open.getClosedAt()).isNull();
            assertThat(open.getClosedBy()).isNull();
        });

        Page<CashSessionResponse> closedOnly = cashSessionService.getSessionHistory(
            CashSessionStatus.CLOSED, PageRequest.of(0, 2));
        assertThat(closedOnly.getContent()).extracting(CashSessionResponse::getId)
            .containsExactly(newerClosedId, olderClosedId);
        assertThat(closedOnly.getContent().getFirst()).satisfies(closed -> {
            assertThat(closed.getOpeningCash()).isEqualByComparingTo("200.0000");
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("240.0000");
            assertThat(closed.getActualClosingCash()).isEqualByComparingTo("241.0000");
            assertThat(closed.getDifference()).isEqualByComparingTo("1.0000");
            assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CLOSED);
            assertThat(closed.getOpenedAt()).isEqualTo(
                java.time.Instant.parse("2126-08-20T01:02:03Z"));
            assertThat(closed.getOpenedBy()).isEqualTo("admin");
            assertThat(closed.getClosedAt()).isEqualTo(
                java.time.Instant.parse("2126-08-20T10:10:11Z"));
            assertThat(closed.getClosedBy()).isEqualTo("admin");
        });

        sessionFactory.getStatistics().clear();
        CashSessionResponse detail = cashSessionService.getSessionDetails(newerClosedId);
        assertThat(detail.getActualClosingCash()).isEqualByComparingTo("241.0000");
        assertThat(detail.getOpenedBy()).isEqualTo("admin");
        assertThat(detail.getClosedBy()).isEqualTo("admin");
        assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isEqualTo(1);
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
    void completedCheckoutCanBeRecoveredWithItsCompleteBackendConfirmedSaleResponse() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("RECOVER", "10.0000", "5.0000");
        String checkoutKey = "recover-checkout-" + UUID.randomUUID();

        SaleResponse created = saleService.createSale(
            checkoutKey,
            checkoutRequest(PaymentType.CASH, "25.0000", item.sku(), "2.0000"));
        SaleCheckoutStatusResponse recovered = saleService.getCheckoutStatus(checkoutKey);

        assertThat(recovered.getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.COMPLETED);
        assertThat(recovered.getSale()).satisfies(sale -> {
            assertThat(sale.getCode()).isEqualTo(created.getCode());
            assertThat(sale.getSessionId()).isEqualTo(sessionId);
            assertThat(sale.getSubtotalAmount()).isEqualByComparingTo("20.0000");
            assertThat(sale.getDiscountAmount()).isEqualByComparingTo("0.0000");
            assertThat(sale.getTotalAmount()).isEqualByComparingTo("20.0000");
            assertThat(sale.getPaidAmount()).isEqualByComparingTo("25.0000");
            assertThat(sale.getChangeAmount()).isEqualByComparingTo("5.0000");
            assertThat(sale.getPaymentType()).isEqualTo(PaymentType.CASH);
            assertThat(sale.getCreatedAt()).isNotNull();
            assertThat(sale.getUpdatedAt()).isNotNull();
            assertThat(sale.getCreatedBy()).isEqualTo("admin");
            assertThat(sale.getUpdatedBy()).isEqualTo("admin");
            assertThat(sale.getSaleItems()).singleElement().satisfies(line -> {
                assertThat(line.getItem().getSku()).isEqualTo(item.sku());
                assertThat(line.getQuantity()).isEqualByComparingTo("2.0000");
                assertThat(line.getUnitPrice()).isEqualByComparingTo("10.0000");
                assertThat(line.getSubtotal()).isEqualByComparingTo("20.0000");
            });
        });
    }

    @Test
    void checkoutStatusWaitsForInFlightSameKeyCheckoutCommitThenReturnsCompleted()
            throws Exception {
        authenticateAdmin();
        cashSessionService.openSession(openRequest("100.0000"));
        ItemFixture item = insertCheckoutItem("STATUS-WAIT-COMMIT", "10.0000", "5.0000");
        String checkoutKey = "status-wait-commit-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");
        SecurityContextHolder.clearContext();

        CountDownLatch checkoutReadyToCommit = new CountDownLatch(1);
        CountDownLatch releaseCheckout = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<SaleResponse> checkout = CompletableFuture.supplyAsync(() -> {
                authenticateAdmin();
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        SaleResponse created = saleService.createSale(checkoutKey, request);
                        checkoutReadyToCommit.countDown();
                        awaitLatch(releaseCheckout);
                        return created;
                    });
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }, executor);
            assertThat(checkoutReadyToCommit.await(10, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<SaleCheckoutStatusResponse> lookup = CompletableFuture.supplyAsync(
                () -> saleService.getCheckoutStatus(checkoutKey), executor);
            awaitAdvisoryLockWaiter(lookup);

            releaseCheckout.countDown();
            SaleResponse created = checkout.get(10, TimeUnit.SECONDS);
            SaleCheckoutStatusResponse recovered = lookup.get(10, TimeUnit.SECONDS);

            assertThat(recovered.getStatus())
                .isEqualTo(SaleCheckoutStatusResponse.Status.COMPLETED);
            assertThat(recovered.getSale().getCode()).isEqualTo(created.getCode());
        } finally {
            releaseCheckout.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void checkoutStatusWaitsForInFlightSameKeyCheckoutRollbackThenReturnsUnknown()
            throws Exception {
        authenticateAdmin();
        cashSessionService.openSession(openRequest("100.0000"));
        ItemFixture item = insertCheckoutItem("STATUS-WAIT-ROLLBACK", "10.0000", "5.0000");
        String checkoutKey = "status-wait-rollback-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");
        long cashMovementCountBefore = cashMovementRepository.count();
        SecurityContextHolder.clearContext();

        CountDownLatch checkoutReadyToRollback = new CountDownLatch(1);
        CountDownLatch releaseCheckout = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> checkout = CompletableFuture.runAsync(() -> {
                authenticateAdmin();
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        saleService.createSale(checkoutKey, request);
                        checkoutReadyToRollback.countDown();
                        awaitLatch(releaseCheckout);
                        status.setRollbackOnly();
                    });
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }, executor);
            assertThat(checkoutReadyToRollback.await(10, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<SaleCheckoutStatusResponse> lookup = CompletableFuture.supplyAsync(
                () -> saleService.getCheckoutStatus(checkoutKey), executor);
            awaitAdvisoryLockWaiter(lookup);

            releaseCheckout.countDown();
            checkout.get(10, TimeUnit.SECONDS);
            SaleCheckoutStatusResponse recovered = lookup.get(10, TimeUnit.SECONDS);

            assertThat(recovered.getStatus())
                .isEqualTo(SaleCheckoutStatusResponse.Status.UNKNOWN);
            assertThat(recovered.getSale()).isNull();
            assertThat(saleRepository.findByCheckoutIdempotencyKey(checkoutKey)).isEmpty();
            assertThat(stockMovementRepository.findByProductId(item.id())).isEmpty();
            assertThat(cashMovementRepository.count()).isEqualTo(cashMovementCountBefore);
            assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
                .isEqualByComparingTo("5.0000");
        } finally {
            releaseCheckout.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unusedCheckoutLookupReturnsUnknownWithoutAnyMutation() {
        ItemFixture item = insertCheckoutItem("UNKNOWN", "10.0000", "5.0000");
        String checkoutKey = "unused-checkout-" + UUID.randomUUID();
        long saleCountBefore = saleRepository.count();
        long movementCountBefore = stockMovementRepository.count();
        long cashMovementCountBefore = cashMovementRepository.count();

        SaleCheckoutStatusResponse response = saleService.getCheckoutStatus(checkoutKey);

        assertThat(response.getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.UNKNOWN);
        assertThat(response.getSale()).isNull();
        assertThat(saleRepository.count()).isEqualTo(saleCountBefore);
        assertThat(stockMovementRepository.count()).isEqualTo(movementCountBefore);
        assertThat(cashMovementRepository.count()).isEqualTo(cashMovementCountBefore);
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("5.0000");
    }

    @Test
    void unknownThenIdenticalPostCreatesOnceAndBecomesCompleted() {
        authenticateAdmin();
        cashSessionService.openSession(openRequest("100.0000"));
        ItemFixture item = insertCheckoutItem("UNKNOWN-THEN-POST", "10.0000", "5.0000");
        String checkoutKey = "unknown-then-post-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");

        assertThat(saleService.getCheckoutStatus(checkoutKey).getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.UNKNOWN);
        SaleResponse created = saleService.createSale(checkoutKey, request);
        SaleResponse retried = saleService.createSale(checkoutKey, request);
        SaleCheckoutStatusResponse recovered = saleService.getCheckoutStatus(checkoutKey);

        assertThat(retried.getCode()).isEqualTo(created.getCode());
        assertThat(recovered.getStatus())
            .isEqualTo(SaleCheckoutStatusResponse.Status.COMPLETED);
        assertThat(recovered.getSale().getCode()).isEqualTo(created.getCode());
        var persisted = saleRepository.findByCheckoutIdempotencyKey(checkoutKey).orElseThrow();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE checkout_idempotency_key = ?",
            Long.class, checkoutKey)).isEqualTo(1L);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
            MovementSourceType.SALE, persisted.getId())).hasSize(1);
        assertThat(cashMovementRepository.findByIdempotencyKey(
            "SALE_PAYMENT:" + persisted.getId())).isPresent();
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("4.0000");
    }

    @Test
    void qrisRequiresExactPaymentAndHasNoPhysicalCashMovement() {
        ItemFixture item = insertCheckoutItem("QRIS", "10.0000", "5.0000");
        assertThatThrownBy(() -> saleService.createSale(
            "qris-no-session-" + UUID.randomUUID(),
            checkoutRequest(PaymentType.QRIS, "10.0000", item.sku(), "1.0000")))
            .isInstanceOf(CashSessionConflictException.class);

        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();

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
    void concurrentCheckoutsForLastStockCommitOnceAndReturnStockConflict() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("LAST-STOCK", "10.0000", "1.0000");
        String firstKey = "last-stock-first-" + UUID.randomUUID();
        String secondKey = "last-stock-second-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> saleService.createSale(firstKey, request),
            () -> saleService.createSale(secondKey, request));

        assertThat(outcomes).filteredOn(SaleResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessException.class::isInstance)
            .singleElement()
            .satisfies(outcome -> assertThat(((BusinessException) outcome).getErrorCode())
                .isEqualTo(ErrorCode.SALE_INSUFFICIENT_STOCK_STORE));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales WHERE checkout_idempotency_key IN (?, ?)",
            Long.class, firstKey, secondKey)).isEqualTo(1L);
        SaleResponse committed = (SaleResponse) outcomes.stream()
            .filter(SaleResponse.class::isInstance)
            .findFirst()
            .orElseThrow();
        var persisted = saleRepository.findByCode(committed.getCode()).orElseThrow();
        assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
            .isEqualByComparingTo("0.0000");
        assertThat(stockMovementRepository.findByProductId(item.id())).hasSize(1);
        assertThat(cashMovementRepository.findByIdempotencyKey(
            "SALE_PAYMENT:" + persisted.getId())).isPresent();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("110.0000");
    }

    @Test
    void checkoutRacingSessionCloseIsCommittedAndIncludedOrRejectedAsConflict()
            throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ItemFixture item = insertCheckoutItem("SESSION-RACE", "10.0000", "5.0000");
        String checkoutKey = "session-race-" + UUID.randomUUID();
        CreateSaleRequest request = checkoutRequest(
            PaymentType.CASH, "10.0000", item.sku(), "1.0000");
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> saleService.createSale(checkoutKey, request),
            () -> cashSessionService.closeSession(sessionId, closeRequest("100.0000")));

        assertThat(outcomes).anyMatch(CashSessionResponse.class::isInstance);
        Object checkoutOutcome = outcomes.stream()
            .filter(outcome -> !(outcome instanceof CashSessionResponse))
            .findFirst()
            .orElseThrow();
        CashSessionResponse closed = cashSessionService.getSessionDetails(sessionId);
        assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CLOSED);

        if (checkoutOutcome instanceof SaleResponse) {
            var sale = saleRepository.findByCheckoutIdempotencyKey(checkoutKey).orElseThrow();
            assertThat(stockMovementRepository.findBySourceTypeAndSourceId(
                MovementSourceType.SALE, sale.getId())).hasSize(1);
            assertThat(cashMovementRepository.findByIdempotencyKey(
                "SALE_PAYMENT:" + sale.getId())).isPresent();
            assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
                .isEqualByComparingTo("4.0000");
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("110.0000");
            assertThat(closed.getDifference()).isEqualByComparingTo("-10.0000");
        } else {
            assertThat(checkoutOutcome).isInstanceOf(CashSessionConflictException.class);
            assertThat(saleRepository.findByCheckoutIdempotencyKey(checkoutKey)).isEmpty();
            assertThat(stockMovementRepository.findByProductId(item.id())).isEmpty();
            assertThat(itemRepository.findById(item.id()).orElseThrow().getStockStore())
                .isEqualByComparingTo("5.0000");
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo("100.0000");
            assertThat(closed.getDifference()).isEqualByComparingTo("0.0000");
        }
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
        String createKey = "owner-expense-" + UUID.randomUUID();
        ExpenseResponse created = expenseService.createExpense(createKey, expenseRequest(sessionId,
            "25.0000", ExpenseCategory.OWNER_WITHDRAWAL, "Owner draw"));
        ExpenseResponse retried = expenseService.createExpense(createKey, expenseRequest(sessionId,
            "25.0", ExpenseCategory.OWNER_WITHDRAWAL, " Owner draw "));
        ExpenseResponse persisted = expenseService.getExpense(created.getId());

        assertThat(retried.getId()).isEqualTo(created.getId());
        assertThat(created.getCashSessionId()).isEqualTo(sessionId);
        assertThat(created.isOperationalExpense()).isFalse();
        assertThat(created.isVoided()).isFalse();
        assertThat(created.isCanVoid()).isTrue();
        assertThat(created.getVoidBlockReason()).isNull();
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("75.0000");
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1L);
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isZero();
        assertThatThrownBy(() -> expenseService.createExpense(createKey, expenseRequest(sessionId,
            "26.0000", ExpenseCategory.OWNER_WITHDRAWAL, "Owner draw")))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
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
        assertThat(voided.isCanVoid()).isFalse();
        assertThat(voided.getVoidBlockReason()).isEqualTo(ExpenseVoidBlockReason.ALREADY_VOIDED);
        assertThat(outcomes).allSatisfy(outcome ->
            assertVoidResponseMatchesStoredAudit((ExpenseResponse) outcome, voided));
        assertThat(voided).usingRecursiveComparison()
            .ignoringFields("voided", "voidedReason", "voidedAt", "voidedBy", "version",
                "canVoid", "voidBlockReason")
            .isEqualTo(persisted);
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1L);
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isEqualTo(1L);
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
    }

    @Test
    void concurrentExpenseCreateRetryWritesOneExpenseAndOneCashOut() throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        String createKey = "concurrent-expense-" + UUID.randomUUID();
        CreateExpenseRequest request = expenseRequest(sessionId,
            "10.0000", ExpenseCategory.STORE_OPERATIONAL, "Cleaning supplies");
        long expensesBefore = expenseRepository.count();
        long movementsBefore = cashMovementRepository.count();
        SecurityContextHolder.clearContext();

        List<Object> outcomes = race(
            () -> expenseService.createExpense(createKey, request),
            () -> expenseService.createExpense(createKey, request)
        );

        assertThat(outcomes).allMatch(ExpenseResponse.class::isInstance);
        assertThat(outcomes).extracting(outcome -> ((ExpenseResponse) outcome).getId())
            .containsOnly(((ExpenseResponse) outcomes.getFirst()).getId());
        var expense = expenseRepository.findByCreateIdempotencyKey(createKey).orElseThrow();
        assertThat(expenseRepository.count()).isEqualTo(expensesBefore + 1);
        assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore + 1);
        assertThat(expense.getCashSession().getId()).isEqualTo(sessionId);
        assertThat(movementCount(expense.getId(), "EXPENSE")).isEqualTo(1L);
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("90.0000");
    }

    @Test
    void committedExpenseReplaysInClosedSessionAWhileBIsOpen() {
        authenticateAdmin();
        Long sessionA = cashSessionService.openSession(openRequest("100.0000")).getId();
        String key = "closed-replay-" + UUID.randomUUID();
        CreateExpenseRequest request = expenseRequest(
            sessionA, "12.5000", ExpenseCategory.FOOD_AND_DRINK, "Team meal");
        ExpenseResponse created = expenseService.createExpense(key, request);
        ExpenseResponse persisted = expenseService.getExpense(created.getId());
        cashSessionService.closeSession(sessionA, closeRequest("87.5000"));
        Long sessionB = cashSessionService.openSession(openRequest("200.0000")).getId();
        long expensesBefore = expenseRepository.count();
        long movementsBefore = cashMovementRepository.count();

        ExpenseResponse replayed = expenseService.createExpense(key, expenseRequest(
            sessionA, "12.5", ExpenseCategory.FOOD_AND_DRINK, " Team meal "));

        assertThat(replayed).usingRecursiveComparison()
            .ignoringFields("canVoid", "voidBlockReason").isEqualTo(persisted);
        assertThat(replayed.isCanVoid()).isFalse();
        assertThat(replayed.getVoidBlockReason()).isEqualTo(ExpenseVoidBlockReason.CASH_SESSION_CLOSED);
        assertThat(replayed.getCashSessionId()).isEqualTo(sessionA);
        assertThatThrownBy(() -> expenseService.createExpense(key, expenseRequest(
            sessionB, "12.5", ExpenseCategory.FOOD_AND_DRINK, "Team meal")))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        assertThatThrownBy(() -> expenseService.createExpense(key, expenseRequest(
            sessionA, "13", ExpenseCategory.FOOD_AND_DRINK, "Team meal")))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        assertThatThrownBy(() -> expenseService.createExpense(key, expenseRequest(
            sessionA, "12.5", ExpenseCategory.CHARITY, "Team meal")))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        assertThatThrownBy(() -> expenseService.createExpense(key, expenseRequest(
            sessionA, "12.5", ExpenseCategory.FOOD_AND_DRINK, "Changed")))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);

        assertThat(expenseRepository.count()).isEqualTo(expensesBefore);
        assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1);
        assertThat(cashSessionService.getSessionDetails(sessionB).getExpectedClosingCash())
            .isEqualByComparingTo("200.0000");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleConfirmationOrRolledBackAttemptCannotPostIntoReplacementSession(boolean attempted) {
        authenticateAdmin();
        Long sessionA = cashSessionService.openSession(openRequest("100.0000")).getId();
        String key = "stale-confirmation-" + UUID.randomUUID();
        CreateExpenseRequest request = expenseRequest(
            sessionA, "10.0000", ExpenseCategory.CHARITY, null);
        long expensesBefore = expenseRepository.count();
        long movementsBefore = cashMovementRepository.count();
        if (attempted) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                expenseService.createExpense(key, request);
                status.setRollbackOnly();
            });
        }
        cashSessionService.closeSession(sessionA, closeRequest("100.0000"));
        Long sessionB = cashSessionService.openSession(openRequest("200.0000")).getId();

        assertThatThrownBy(() -> expenseService.createExpense(key, request))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining(sessionA.toString());

        assertThat(expenseRepository.findByCreateIdempotencyKey(key)).isEmpty();
        assertThat(expenseRepository.count()).isEqualTo(expensesBefore);
        assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(cashSessionService.getSessionDetails(sessionA).getExpectedClosingCash())
            .isEqualByComparingTo("100.0000");
        assertThat(cashSessionService.getSessionDetails(sessionB).getExpectedClosingCash())
            .isEqualByComparingTo("200.0000");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void expenseAndSessionCloseSerializeBothLockOrders(boolean expenseFirst) throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        String key = "expense-close-race-" + UUID.randomUUID();
        CreateExpenseRequest request = expenseRequest(
            sessionId, "10.0000", ExpenseCategory.CHARITY, null);
        long expensesBefore = expenseRepository.count();
        long movementsBefore = cashMovementRepository.count();
        Supplier<?> create = () -> expenseService.createExpense(key, request);
        Supplier<?> close = () -> cashSessionService.closeSession(sessionId, closeRequest("100.0000"));
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondBackendPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Object> first = authenticatedOutcome(executor, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    Object result = (expenseFirst ? create : close).get();
                    firstReady.countDown();
                    awaitLatch(releaseFirst);
                    return result;
                }));
            assertThat(firstReady.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Object> second = authenticatedOutcome(executor, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    secondBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    return (expenseFirst ? close : create).get();
                }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseBlocker(secondBackendPid.get(), second);

            // Even a flushed expense/movement is invisible until the shared transaction commits.
            assertThat(expenseRepository.count()).isEqualTo(expensesBefore);
            assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore);
            releaseFirst.countDown();
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            if (expenseFirst) {
                assertThat(firstResult).isInstanceOf(ExpenseResponse.class);
                assertThat(secondResult).isInstanceOf(CashSessionResponse.class);
                ExpenseResponse created = (ExpenseResponse) firstResult;
                assertThat(created.getCashSessionId()).isEqualTo(sessionId);
                assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1);
                assertThat(expenseRepository.count()).isEqualTo(expensesBefore + 1);
                assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore + 1);
            } else {
                assertThat(firstResult).isInstanceOf(CashSessionResponse.class);
                assertThat(secondResult).isInstanceOf(CashSessionConflictException.class);
                assertThat(expenseRepository.findByCreateIdempotencyKey(key)).isEmpty();
                assertThat(expenseRepository.count()).isEqualTo(expensesBefore);
                assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore);
            }
            CashSessionResponse closed = cashSessionService.getSessionDetails(sessionId);
            assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CLOSED);
            assertThat(closed.getExpectedClosingCash())
                .isEqualByComparingTo(expenseFirst ? "90.0000" : "100.0000");
            assertThat(closed.getDifference())
                .isEqualByComparingTo(expenseFirst ? "10.0000" : "0.0000");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void expenseEligibilityReadsAllSessionStatesWithoutWritesOrPerRowQueries() {
        authenticateAdmin();
        Long closedSessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ExpenseResponse activeClosed = expenseService.createExpense(
            "closed-active-" + UUID.randomUUID(), expenseRequest(
                closedSessionId, "10", ExpenseCategory.CHARITY, null));
        ExpenseResponse voidedClosed = expenseService.createExpense(
            "closed-voided-" + UUID.randomUUID(), expenseRequest(
                closedSessionId, "10", ExpenseCategory.CHARITY, null));
        expenseService.voidExpense(voidedClosed.getId(), voidRequest("Duplicate"));
        cashSessionService.closeSession(closedSessionId, closeRequest("90"));
        Long openSessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ExpenseResponse activeOpen = expenseService.createExpense(
            "open-active-" + UUID.randomUUID(), expenseRequest(
                openSessionId, "10", ExpenseCategory.CHARITY, null));
        ExpenseResponse voidedOpen = expenseService.createExpense(
            "open-voided-" + UUID.randomUUID(), expenseRequest(
                openSessionId, "10", ExpenseCategory.CHARITY, null));
        expenseService.voidExpense(voidedOpen.getId(), voidRequest("Duplicate"));

        var before = jdbcTemplate.queryForList("SELECT * FROM expenses ORDER BY id");
        long movementsBefore = cashMovementRepository.count();
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();

        Page<ExpenseResponse> page = expenseService.getExpenses(PageRequest.of(0, 4));

        assertThat(sessionFactory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(2);
        assertThat(page.getContent()).extracting(ExpenseResponse::getId)
            .containsExactly(voidedOpen.getId(), activeOpen.getId(), voidedClosed.getId(), activeClosed.getId());
        assertThat(page.getContent()).extracting(ExpenseResponse::isCanVoid)
            .containsExactly(false, true, false, false);
        assertThat(page.getContent()).extracting(ExpenseResponse::getVoidBlockReason)
            .containsExactly(ExpenseVoidBlockReason.ALREADY_VOIDED, null,
                ExpenseVoidBlockReason.ALREADY_VOIDED, ExpenseVoidBlockReason.CASH_SESSION_CLOSED);
        for (ExpenseResponse expense : page) {
            assertThat(expenseService.getExpense(expense.getId())).isEqualTo(expense);
        }
        assertThat(sessionFactory.getStatistics().getEntityInsertCount()).isZero();
        assertThat(sessionFactory.getStatistics().getEntityUpdateCount()).isZero();
        assertThat(sessionFactory.getStatistics().getEntityDeleteCount()).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT * FROM expenses ORDER BY id")).isEqualTo(before);
        assertThat(cashMovementRepository.count()).isEqualTo(movementsBefore);
    }

    @Test
    void staleEligibilityReplaysFirstVoidAuditEvenWithDifferentReasonAndAfterClose() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100")).getId();
        ExpenseResponse created = expenseService.createExpense(
            "stale-void-" + UUID.randomUUID(), expenseRequest(
                sessionId, "10", ExpenseCategory.CHARITY, "Original note"));
        assertThat(expenseService.getExpense(created.getId()).isCanVoid()).isTrue();
        ExpenseResponse returned = expenseService.voidExpense(created.getId(), voidRequest("  Duplicate  "));
        ExpenseResponse voided = expenseService.getExpense(created.getId());
        assertVoidResponseMatchesStoredAudit(returned, voided);
        assertThat(voided.getVoidedReason()).isEqualTo("Duplicate");

        // A later caller's identity/reason cannot replace the confirmed audit result.
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("later-caller", "ignored", List.of()));
        assertThat(expenseService.voidExpense(created.getId(), voidRequest("Different reason")))
            .isEqualTo(voided);
        authenticateAdmin();
        cashSessionService.closeSession(sessionId, closeRequest("100"));
        var before = jdbcTemplate.queryForList("SELECT * FROM expenses WHERE id = ?", created.getId());
        assertThat(expenseService.voidExpense(created.getId(), voidRequest("Retry after close")))
            .isEqualTo(voided);
        assertThat(expenseService.getExpense(created.getId())).isEqualTo(voided);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM expenses WHERE id = ?", created.getId()))
            .isEqualTo(before);
        assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1);
        assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isEqualTo(1);
        assertThat(cashSessionService.getSessionDetails(sessionId).getExpectedClosingCash())
            .isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void expenseVoidAndSessionCloseSerializeBothLockOrders(boolean voidFirst) throws Exception {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100")).getId();
        ExpenseResponse created = expenseService.createExpense(
            "void-close-race-" + UUID.randomUUID(), expenseRequest(
                sessionId, "10", ExpenseCategory.CHARITY, null));
        ExpenseResponse persisted = expenseService.getExpense(created.getId());
        assertThat(expenseService.getExpense(created.getId()).isCanVoid()).isTrue();
        Supplier<?> reverse = () -> expenseService.voidExpense(created.getId(), voidRequest("Duplicate"));
        Supplier<?> close = () -> cashSessionService.closeSession(sessionId, closeRequest("100"));
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondBackendPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Object> first = authenticatedOutcome(executor, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    Object result = (voidFirst ? reverse : close).get();
                    firstReady.countDown();
                    awaitLatch(releaseFirst);
                    return result;
                }));
            assertThat(firstReady.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Object> second = authenticatedOutcome(executor, () ->
                new TransactionTemplate(transactionManager).execute(status -> {
                    secondBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    return (voidFirst ? close : reverse).get();
                }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseBlocker(secondBackendPid.get(), second);
            assertThat(expenseService.getExpense(created.getId()).isVoided()).isFalse();
            assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isZero();
            releaseFirst.countDown();
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            ExpenseResponse stored = expenseService.getExpense(created.getId());
            assertThat(stored.isCanVoid()).isFalse();
            if (voidFirst) {
                assertThat(firstResult).isInstanceOf(ExpenseResponse.class);
                assertVoidResponseMatchesStoredAudit((ExpenseResponse) firstResult, stored);
                assertThat(secondResult).isInstanceOf(CashSessionResponse.class);
                assertThat(stored.getVoidBlockReason()).isEqualTo(ExpenseVoidBlockReason.ALREADY_VOIDED);
                assertThat(expenseService.voidExpense(created.getId(), voidRequest("Retry"))).isEqualTo(stored);
            } else {
                assertThat(firstResult).isInstanceOf(CashSessionResponse.class);
                assertThat(secondResult).isInstanceOf(CashSessionConflictException.class);
                assertThat(stored).usingRecursiveComparison()
                    .ignoringFields("canVoid", "voidBlockReason").isEqualTo(persisted);
                assertThat(stored.getVoidBlockReason()).isEqualTo(ExpenseVoidBlockReason.CASH_SESSION_CLOSED);
            }
            assertThat(movementCount(created.getId(), "EXPENSE")).isEqualTo(1);
            assertThat(movementCount(created.getId(), "EXPENSE_REVERSAL")).isEqualTo(voidFirst ? 1 : 0);
            CashSessionResponse closed = cashSessionService.getSessionDetails(sessionId);
            assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CLOSED);
            assertThat(closed.getExpectedClosingCash()).isEqualByComparingTo(voidFirst ? "100" : "90");
            assertThat(closed.getDifference()).isEqualByComparingTo(voidFirst ? "0" : "10");
            assertThat(cashSessionService.calculateExpectedCash(sessionId).getExpectedClosingCash())
                .isEqualByComparingTo(closed.getExpectedClosingCash());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertVoidResponseMatchesStoredAudit(ExpenseResponse returned, ExpenseResponse stored) {
        assertThat(returned).usingRecursiveComparison().ignoringFields("voidedAt").isEqualTo(stored);
        // PostgreSQL stores microseconds; the first response retains Instant.now() precision.
        assertThat(returned.getVoidedAt()).isCloseTo(stored.getVoidedAt(), within(1, ChronoUnit.MICROS));
    }

    private CompletableFuture<Object> authenticatedOutcome(ExecutorService executor, Supplier<?> operation) {
        return CompletableFuture.supplyAsync(() -> {
            authenticateAdmin();
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                return exception;
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, executor);
    }

    private void awaitDatabaseBlocker(int backendPid, CompletableFuture<?> operation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid);
            if (Boolean.TRUE.equals(blocked)) {
                assertThat(operation).isNotDone();
                return;
            }
            assertThat(operation).as("Operation must wait for the session lock").isNotDone();
            Thread.sleep(25);
        }
        throw new AssertionError("Operation did not wait for the session lock");
    }

    @Test
    void closedSessionRejectsExpenseVoidAndDatabaseRejectsEditOrDelete() {
        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        ExpenseResponse created = expenseService.createExpense(
            "other-expense-" + UUID.randomUUID(), expenseRequest(sessionId,
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
        assertThatThrownBy(() -> expenseService.createExpense(
            "no-session-" + UUID.randomUUID(), expenseRequest(Long.MAX_VALUE,
            "1.0000", ExpenseCategory.CHARITY, null)))
            .isInstanceOf(CashSessionConflictException.class);

        authenticateAdmin();
        Long sessionId = cashSessionService.openSession(openRequest("100.0000")).getId();
        assertThatThrownBy(() -> expenseService.createExpense(
            "zero-expense-" + UUID.randomUUID(), expenseRequest(sessionId,
            "0.0000", ExpenseCategory.CHARITY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
        assertThatThrownBy(() -> expenseService.createExpense(
            "blank-other-" + UUID.randomUUID(), expenseRequest(sessionId,
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
        assertThatThrownBy(() -> expenseService.createExpense(
            "ledger-conflict-" + UUID.randomUUID(), expenseRequest(sessionId,
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

    private void awaitAdvisoryLockWaiter(CompletableFuture<?> lookup) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waiters = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND wait_event_type = 'Lock'
                  AND wait_event = 'advisory'
                """, Long.class);
            if (waiters != null && waiters > 0) {
                assertThat(lookup).isNotDone();
                return;
            }
            if (lookup.isDone()) {
                throw new AssertionError(
                    "Checkout-status returned before the same-key checkout transaction completed");
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Checkout-status did not wait on the advisory lock");
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test coordination", exception);
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
            Long sessionId, String amount, ExpenseCategory category, String description) {
        return CreateExpenseRequest.builder()
            .expectedCashSessionId(sessionId)
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
                name, sku, price, stock_store, stock_warehouse,
                base_unit_of_measure, fractional_quantity_allowed, active,
                item_category_id, version
            ) VALUES (?, ?, ?, ?, 0.0000, 'PIECE', FALSE, TRUE, 1, 0)
            RETURNING id
            """, Long.class, purpose + " checkout item", sku,
            new BigDecimal(price), new BigDecimal(storeStock));
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
