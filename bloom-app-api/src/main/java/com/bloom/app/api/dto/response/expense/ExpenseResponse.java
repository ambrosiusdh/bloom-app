package com.bloom.app.api.dto.response.expense;

import com.bloom.app.domain.enums.ExpenseCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private Long cashSessionId;
    private BigDecimal amount;
    private ExpenseCategory category;
    private boolean operationalExpense;
    private String description;
    private boolean voided;
    private String voidedReason;
    private Instant voidedAt;
    private String voidedBy;
    private Instant createdAt;
    private String createdBy;
    private Long version;
}
