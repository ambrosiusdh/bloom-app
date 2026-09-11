package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ExpenseIdempotencyConflictException;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Expense;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.ExpenseRepository;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import com.bloom.app.service.mapper.ExpenseMapper;
import com.bloom.app.service.util.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExpenseServiceImplTest {
    private ExpenseRepository expenseRepository;
    private CashSessionRepository cashSessionRepository;
    private CashMovementService cashMovementService;
    private ExpenseMapper expenseMapper;
    private CurrentActorProvider currentActorProvider;
    private ExpenseServiceImpl service;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        cashSessionRepository = mock(CashSessionRepository.class);
        cashMovementService = mock(CashMovementService.class);
        expenseMapper = mock(ExpenseMapper.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        service = new ExpenseServiceImpl(
            expenseRepository,
            cashSessionRepository,
            cashMovementService,
            expenseMapper,
            currentActorProvider
        );
    }

    @Test
    void createsExpenseAndMatchingCashOutAgainstOpenSession() {
        CashSession session = openSession();
        ExpenseResponse expected = ExpenseResponse.builder().id(41L).build();
        when(cashSessionRepository.findByIdForUpdate(7L))
            .thenReturn(Optional.of(session));
        when(expenseRepository.findByCreateIdempotencyKey("expense-41"))
            .thenReturn(Optional.empty());
        when(expenseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(41L);
            return expense;
        });
        when(expenseMapper.toResponse(any())).thenReturn(expected);

        ExpenseResponse actual = service.createExpense(" expense-41 ", CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(new BigDecimal("12.5"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description("  Team meal  ")
            .build());

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).saveAndFlush(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getCashSession()).isSameAs(session);
        assertThat(expenseCaptor.getValue().getAmount()).isEqualByComparingTo("12.5000");
        assertThat(expenseCaptor.getValue().getDescription()).isEqualTo("Team meal");
        assertThat(expenseCaptor.getValue().getCreateIdempotencyKey()).isEqualTo("expense-41");
        assertThat(expenseCaptor.getValue().getCreateRequestHash())
            .isEqualTo("34b6f15b9c6a68c2cc35c29b02502a128a5e72975e82ba53d0b18b9b62d25352");
        verify(expenseRepository).lockCreateIdempotencyKey("expense-41");
        var order = inOrder(expenseRepository, cashSessionRepository, cashMovementService);
        order.verify(expenseRepository).lockCreateIdempotencyKey("expense-41");
        order.verify(expenseRepository).findByCreateIdempotencyKey("expense-41");
        order.verify(cashSessionRepository).findByIdForUpdate(7L);
        order.verify(expenseRepository).saveAndFlush(any());
        order.verify(cashMovementService).recordMovement(any());

        ArgumentCaptor<RecordCashMovementCommand> movementCaptor =
            ArgumentCaptor.forClass(RecordCashMovementCommand.class);
        verify(cashMovementService).recordMovement(movementCaptor.capture());
        assertThat(movementCaptor.getValue()).satisfies(command -> {
            assertThat(command.sessionId()).isEqualTo(7L);
            assertThat(command.movementType()).isEqualTo(CashMovementType.EXPENSE);
            assertThat(command.sourceId()).isEqualTo(41L);
            assertThat(command.referenceNo()).isEqualTo("EXPENSE-41");
            assertThat(command.amount()).isEqualByComparingTo("12.5000");
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsMissingOrNonPositiveExpectedSessionBeforeAnyPersistence(Long sessionId) {
        CreateExpenseRequest request = CreateExpenseRequest.builder()
            .expectedCashSessionId(sessionId)
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build();

        assertThatThrownBy(() -> service.createExpense("invalid-session", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expected cash session ID must be positive");
        verifyNoInteractions(expenseRepository, cashSessionRepository, cashMovementService);
    }

    @Test
    void rejectsClosedExpectedSessionWithoutSelectingAnotherOpenSession() {
        when(cashSessionRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(
            CashSession.builder().id(7L).status(CashSessionStatus.CLOSED).build()));

        assertThatThrownBy(() -> service.createExpense("stale-session", CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build()))
            .isInstanceOf(CashSessionConflictException.class);

        verify(cashSessionRepository, never()).findFirstByStatusForUpdate(any());
        verify(expenseRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cashMovementService);
    }

    @Test
    void requiresDescriptionForOtherAndAnOpenSession() {
        assertThatThrownBy(() -> service.createExpense("other-blank", CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.OTHER)
            .description("   ")
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expense description is required for OTHER category");

        when(cashSessionRepository.findByIdForUpdate(7L))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createExpense("charity-no-session", CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build()))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("no longer available");
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void idempotentRetryReturnsExistingAndChangedPayloadConflicts() {
        Expense existing = Expense.builder()
            .id(41L)
            .cashSession(openSession())
            .amount(new BigDecimal("12.5000"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description("Team meal")
            .createIdempotencyKey("expense-41")
            .createRequestHash("unused")
            .build();
        CreateExpenseRequest request = CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(new BigDecimal("12.5"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description(" Team meal ")
            .build();

        when(expenseRepository.findByCreateIdempotencyKey("expense-41"))
            .thenReturn(Optional.empty());
        when(cashSessionRepository.findByIdForUpdate(7L))
            .thenReturn(Optional.of(openSession()));
        when(expenseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(41L);
            existing.setCreateRequestHash(saved.getCreateRequestHash());
            return saved;
        });
        service.createExpense("expense-41", request);

        when(expenseRepository.findByCreateIdempotencyKey("expense-41"))
            .thenReturn(Optional.of(existing));
        existing.getCashSession().setStatus(CashSessionStatus.CLOSED);
        service.createExpense("expense-41", request);
        assertThatThrownBy(() -> service.createExpense("expense-41",
            CreateExpenseRequest.builder()
                .expectedCashSessionId(7L)
                .amount(new BigDecimal("13.0000"))
                .category(ExpenseCategory.FOOD_AND_DRINK)
                .description("Team meal")
                .build()))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        request.setExpectedCashSessionId(8L);
        assertThatThrownBy(() -> service.createExpense("expense-41", request))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        verify(cashSessionRepository, times(1)).findByIdForUpdate(7L);
        verify(cashSessionRepository, never()).findByIdForUpdate(8L);
        verify(cashMovementService, times(1)).recordMovement(any());
    }

    @Test
    void preFe29HashReplaysAfterCloseOnlyForPersistedSessionAndOriginalContent() {
        Expense existing = Expense.builder()
            .id(41L)
            .cashSession(CashSession.builder().id(7L).status(CashSessionStatus.CLOSED).build())
            .amount(new BigDecimal("12.5000"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description("Team meal")
            .createIdempotencyKey("legacy-key")
            // Fixed fixture from the pre-FE-29, length-prefixed SHA-256 format.
            .createRequestHash("34b6f15b9c6a68c2cc35c29b02502a128a5e72975e82ba53d0b18b9b62d25352")
            .build();
        ExpenseResponse expected = ExpenseResponse.builder().id(41L).cashSessionId(7L).build();
        when(expenseRepository.findByCreateIdempotencyKey("legacy-key"))
            .thenReturn(Optional.of(existing));
        when(expenseMapper.toResponse(existing)).thenReturn(expected);
        CreateExpenseRequest request = CreateExpenseRequest.builder()
            .expectedCashSessionId(7L)
            .amount(new BigDecimal("12.5"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description(" Team meal ")
            .build();

        assertThat(service.createExpense("legacy-key", request)).isSameAs(expected);
        request.setExpectedCashSessionId(8L);
        assertThatThrownBy(() -> service.createExpense("legacy-key", request))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        request.setExpectedCashSessionId(7L);
        request.setCategory(ExpenseCategory.CHARITY);
        assertThatThrownBy(() -> service.createExpense("legacy-key", request))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        request.setCategory(ExpenseCategory.FOOD_AND_DRINK);
        request.setDescription("Different meal");
        assertThatThrownBy(() -> service.createExpense("legacy-key", request))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);
        request.setDescription("Team meal");
        request.setAmount(new BigDecimal("13"));
        assertThatThrownBy(() -> service.createExpense("legacy-key", request))
            .isInstanceOf(ExpenseIdempotencyConflictException.class);

        verifyNoInteractions(cashSessionRepository, cashMovementService);
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void readsExpenseWithExplicitCashSessionFetch() {
        Expense expense = Expense.builder()
            .id(41L)
            .cashSession(openSession())
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build();
        ExpenseResponse expected = ExpenseResponse.builder()
            .id(41L)
            .cashSessionId(7L)
            .build();
        when(expenseRepository.findDetailsById(41L)).thenReturn(Optional.of(expense));
        when(expenseMapper.toResponse(expense)).thenReturn(expected);

        assertThat(service.getExpense(41L)).isSameAs(expected);
        verify(expenseRepository).findDetailsById(41L);
        verify(expenseRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cashSessionRepository, cashMovementService, currentActorProvider);
    }

    @Test
    void listsAllSessionsUsingExplicitCashSessionFetchAndAuditSort() {
        Expense expense = Expense.builder()
            .id(41L)
            .cashSession(openSession())
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build();
        ExpenseResponse expected = ExpenseResponse.builder().id(41L).build();
        Pageable requested = PageRequest.of(2, 10, Sort.by("amount"));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(expenseRepository.findAllWithCashSession(any()))
            .thenReturn(new PageImpl<>(List.of(expense)));
        when(expenseMapper.toResponse(expense)).thenReturn(expected);

        assertThat(service.getExpenses(requested).getContent()).containsExactly(expected);
        verify(expenseRepository).findAllWithCashSession(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort())
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        verify(expenseRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cashSessionRepository, cashMovementService, currentActorProvider);
    }

    @Test
    void voidsOnceWithCompensatingCashInAndAuditMetadata() {
        Expense expense = Expense.builder()
            .id(41L)
            .cashSession(openSession())
            .amount(new BigDecimal("12.5000"))
            .category(ExpenseCategory.CHARITY)
            .build();
        ExpenseResponse expected = ExpenseResponse.builder().id(41L).voided(true).build();
        when(expenseRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(expense));
        when(expenseRepository.saveAndFlush(expense)).thenReturn(expense);
        when(currentActorProvider.username()).thenReturn("admin");
        when(expenseMapper.toResponse(expense)).thenReturn(expected);

        ExpenseResponse actual = service.voidExpense(
            41L, VoidExpenseRequest.builder().reason("  Duplicate receipt  ").build());

        assertThat(actual).isSameAs(expected);
        assertThat(expense.isVoided()).isTrue();
        assertThat(expense.getVoidedReason()).isEqualTo("Duplicate receipt");
        assertThat(expense.getVoidedAt()).isNotNull();
        assertThat(expense.getVoidedBy()).isEqualTo("admin");
        ArgumentCaptor<RecordCashMovementCommand> movementCaptor =
            ArgumentCaptor.forClass(RecordCashMovementCommand.class);
        verify(cashMovementService).recordMovement(movementCaptor.capture());
        assertThat(movementCaptor.getValue().movementType())
            .isEqualTo(CashMovementType.EXPENSE_REVERSAL);
        assertThat(movementCaptor.getValue().referenceNo()).isEqualTo("EXPENSE-41-VOID");
        assertThat(movementCaptor.getValue().amount()).isEqualByComparingTo("12.5000");
    }

    @Test
    void repeatedVoidIsReadOnlyButFirstVoidOnClosedSessionIsRejected() {
        Expense alreadyVoided = Expense.builder()
            .id(41L)
            .cashSession(CashSession.builder().id(7L).status(CashSessionStatus.CLOSED).build())
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.OTHER)
            .description("Audit note")
            .isVoided(true)
            .voidedReason("Already reversed")
            .build();
        when(expenseRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(alreadyVoided));

        service.voidExpense(41L, VoidExpenseRequest.builder().reason("Retry").build());

        verify(cashMovementService, never()).recordMovement(any());
        verify(expenseRepository, never()).saveAndFlush(any());

        Expense activeClosed = Expense.builder()
            .id(42L)
            .cashSession(CashSession.builder().id(7L).status(CashSessionStatus.CLOSED).build())
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.STORE_OPERATIONAL)
            .build();
        when(expenseRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(activeClosed));
        assertThatThrownBy(() -> service.voidExpense(
            42L, VoidExpenseRequest.builder().reason("Too late").build()))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("closed");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void rejectsMissingOrBlankVoidReasonBeforePersistence(String reason) {
        assertThatThrownBy(() -> service.voidExpense(
            41L, VoidExpenseRequest.builder().reason(reason).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Void reason is required");
        verifyNoInteractions(expenseRepository, cashSessionRepository, cashMovementService);
    }

    @Test
    void rejectsOverlongVoidReasonBeforePersistence() {
        assertThatThrownBy(() -> service.voidExpense(
            41L, VoidExpenseRequest.builder().reason("x".repeat(256)).build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Void reason must not exceed 255 characters");
        verifyNoInteractions(expenseRepository, cashSessionRepository, cashMovementService);
    }

    private CashSession openSession() {
        return CashSession.builder().id(7L).status(CashSessionStatus.OPEN).build();
    }
}
