package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.exception.CashSessionConflictException;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(cashSessionRepository.findFirstByStatusForUpdate(CashSessionStatus.OPEN))
            .thenReturn(Optional.of(session));
        when(expenseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(41L);
            return expense;
        });
        when(expenseMapper.toResponse(any())).thenReturn(expected);

        ExpenseResponse actual = service.createExpense(CreateExpenseRequest.builder()
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

    @Test
    void requiresDescriptionForOtherAndAnOpenSession() {
        assertThatThrownBy(() -> service.createExpense(CreateExpenseRequest.builder()
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.OTHER)
            .description("   ")
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expense description is required for OTHER category");

        when(cashSessionRepository.findFirstByStatusForUpdate(CashSessionStatus.OPEN))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createExpense(CreateExpenseRequest.builder()
            .amount(BigDecimal.ONE)
            .category(ExpenseCategory.CHARITY)
            .build()))
            .isInstanceOf(CashSessionConflictException.class)
            .hasMessageContaining("open cash session");
        verify(expenseRepository, never()).saveAndFlush(any());
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

    private CashSession openSession() {
        return CashSession.builder().id(7L).status(CashSessionStatus.OPEN).build();
    }
}
