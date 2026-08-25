package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.expense.CreateExpenseRequest;
import com.bloom.app.api.dto.request.expense.VoidExpenseRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @PostMapping
    @Operation(summary = "Record an unexpected drawer expense")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 100, message = "Idempotency-Key must not exceed 100 characters")
            String idempotencyKey,
            @Valid @RequestBody CreateExpenseRequest request) {
        return ResponseHelper.created(
            "Expense recorded successfully",
            expenseService.createExpense(idempotencyKey, request));
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get an auditable expense record")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpense(
            @PathVariable @Positive Long expenseId) {
        return ResponseHelper.ok(expenseService.getExpense(expenseId));
    }

    @GetMapping
    @Operation(summary = "List auditable expense records")
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getExpenses(Pageable pageable) {
        return ResponseHelper.ok(
            expenseService.getExpenses(PagingHelper.toPageRequest(pageable)));
    }

    @PostMapping("/{expenseId}/void")
    @Operation(summary = "Void an expense with a compensating drawer movement")
    public ResponseEntity<ApiResponse<ExpenseResponse>> voidExpense(
            @PathVariable @Positive Long expenseId,
            @Valid @RequestBody VoidExpenseRequest request) {
        return ResponseHelper.ok(expenseService.voidExpense(expenseId, request));
    }
}
