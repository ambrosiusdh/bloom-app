package com.bloom.app.api.dto.request.expense;

import com.bloom.app.domain.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {
    @NotNull(message = "Expense amount is required")
    @DecimalMin(value = "0.0001", message = "Expense amount must be positive")
    @Digits(integer = 15, fraction = 4,
        message = "Expense amount must have at most 15 integer and 4 decimal digits")
    private BigDecimal amount;

    @NotNull(message = "Expense category is required")
    private ExpenseCategory category;

    @Size(max = 255, message = "Expense description must not exceed 255 characters")
    private String description;
}
