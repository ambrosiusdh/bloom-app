package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Expense;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.ExpenseRepository;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.ExpenseService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import com.bloom.app.service.mapper.ExpenseMapper;
import com.bloom.app.service.util.CashMoneyUtil;
import com.bloom.app.service.util.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {
    private static final int MAX_TEXT_LENGTH = 255;

    private final ExpenseRepository expenseRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashMovementService cashMovementService;
    private final ExpenseMapper expenseMapper;
    private final CurrentActorProvider currentActorProvider;

    @Override
    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create expense request is required");
        }
        BigDecimal amount = CashMoneyUtil.requirePositive(request.getAmount(), "Expense amount");
        ExpenseCategory category = requireCategory(request.getCategory());
        String description = normalizeOptional(request.getDescription(), "Expense description");
        if (category == ExpenseCategory.OTHER && description == null) {
            throw new IllegalArgumentException("Expense description is required for OTHER category");
        }

        CashSession session = cashSessionRepository
            .findFirstByStatusForUpdate(CashSessionStatus.OPEN)
            .orElseThrow(() -> new CashSessionConflictException(
                "An open cash session is required to record an expense"));

        Expense saved = expenseRepository.saveAndFlush(Expense.builder()
            .cashSession(session)
            .amount(amount)
            .category(category)
            .description(description)
            .build());

        cashMovementService.recordMovement(new RecordCashMovementCommand(
            session.getId(),
            CashMovementType.EXPENSE,
            saved.getId(),
            expenseReference(saved.getId()),
            amount
        ));
        return expenseMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long expenseId) {
        validateExpenseId(expenseId);
        Expense expense = expenseRepository.findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        return expenseMapper.toResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(Pageable pageable) {
        Pageable effectivePageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return expenseRepository.findAll(effectivePageable).map(expenseMapper::toResponse);
    }

    @Override
    @Transactional
    public ExpenseResponse voidExpense(Long expenseId, VoidExpenseRequest request) {
        validateExpenseId(expenseId);
        if (request == null) {
            throw new IllegalArgumentException("Void expense request is required");
        }
        String reason = normalizeRequired(request.getReason(), "Void reason");
        Expense expense = expenseRepository.findByIdForUpdate(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));

        if (expense.isVoided()) {
            return expenseMapper.toResponse(expense);
        }

        CashSession session = expense.getCashSession();
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new CashSessionConflictException(
                "Cash session " + session.getId()
                    + " is closed and rejects expense voids that change reconciled cash");
        }

        cashMovementService.recordMovement(new RecordCashMovementCommand(
            session.getId(),
            CashMovementType.EXPENSE_REVERSAL,
            expense.getId(),
            expenseReference(expense.getId()) + "-VOID",
            expense.getAmount()
        ));

        expense.setVoided(true);
        expense.setVoidedReason(reason);
        expense.setVoidedAt(Instant.now());
        expense.setVoidedBy(currentActorProvider.username());
        Expense saved = expenseRepository.saveAndFlush(expense);
        return expenseMapper.toResponse(saved);
    }

    private ExpenseCategory requireCategory(ExpenseCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Expense category is required");
        }
        return category;
    }

    private String normalizeOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeLength(value.trim(), fieldName);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalizeLength(value.trim(), fieldName);
    }

    private String normalizeLength(String value, String fieldName) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must not exceed 255 characters");
        }
        return value;
    }

    private void validateExpenseId(Long expenseId) {
        if (expenseId == null || expenseId <= 0) {
            throw new IllegalArgumentException("Expense ID must be positive");
        }
    }

    private String expenseReference(Long expenseId) {
        return "EXPENSE-" + expenseId;
    }
}
