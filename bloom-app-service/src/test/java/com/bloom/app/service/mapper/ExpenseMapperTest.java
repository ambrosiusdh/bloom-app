package com.bloom.app.service.mapper;

import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.enums.ExpenseVoidBlockReason;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Expense;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseMapperTest {
    private final ExpenseMapper mapper = Mappers.getMapper(ExpenseMapper.class);

    @ParameterizedTest
    @CsvSource({
        "OPEN, false, true,",
        "CLOSED, false, false, CASH_SESSION_CLOSED",
        "OPEN, true, false, ALREADY_VOIDED",
        "CLOSED, true, false, ALREADY_VOIDED"
    })
    void mapsEligibilityAndPreservesOriginalFactsAndAudit(
            CashSessionStatus status, boolean voided, boolean canVoid,
            ExpenseVoidBlockReason blockReason) {
        Instant createdAt = Instant.parse("2026-09-11T01:00:00Z");
        Instant voidedAt = voided ? Instant.parse("2026-09-11T02:00:00Z") : null;
        Expense expense = Expense.builder()
            .id(41L)
            .cashSession(CashSession.builder().id(7L).status(status).build())
            .amount(new BigDecimal("12.5000"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .description("Team meal")
            .createdAt(createdAt)
            .createdBy("cashier")
            .isVoided(voided)
            .voidedReason(voided ? "Duplicate" : null)
            .voidedAt(voidedAt)
            .voidedBy(voided ? "admin" : null)
            .version(voided ? 1L : 0L)
            .build();

        var response = mapper.toResponse(expense);

        assertThat(response.isCanVoid()).isEqualTo(canVoid);
        assertThat(response.getVoidBlockReason()).isEqualTo(blockReason);
        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getCashSessionId()).isEqualTo(7L);
        assertThat(response.getAmount()).isEqualByComparingTo("12.5000");
        assertThat(response.getCategory()).isEqualTo(ExpenseCategory.FOOD_AND_DRINK);
        assertThat(response.isOperationalExpense()).isTrue();
        assertThat(response.getDescription()).isEqualTo("Team meal");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getCreatedBy()).isEqualTo("cashier");
        assertThat(response.isVoided()).isEqualTo(voided);
        assertThat(response.getVoidedReason()).isEqualTo(expense.getVoidedReason());
        assertThat(response.getVoidedAt()).isEqualTo(voidedAt);
        assertThat(response.getVoidedBy()).isEqualTo(expense.getVoidedBy());
        assertThat(response.getVersion()).isEqualTo(expense.getVersion());
    }
}
