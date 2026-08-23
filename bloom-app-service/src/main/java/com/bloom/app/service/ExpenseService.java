package com.bloom.app.service;

import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {
    ExpenseResponse createExpense(String idempotencyKey, CreateExpenseRequest request);

    ExpenseResponse getExpense(Long expenseId);

    Page<ExpenseResponse> getExpenses(Pageable pageable);

    ExpenseResponse voidExpense(Long expenseId, VoidExpenseRequest request);
}
