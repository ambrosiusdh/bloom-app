package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.ExpenseVoidBlockReason;
import com.bloom.app.domain.model.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {
    @Mapping(target = "cashSessionId", source = "cashSession.id")
    @Mapping(target = "operationalExpense", expression = "java(expense.getCategory().isOperationalExpense())")
    @Mapping(target = "canVoid", expression = "java(voidBlockReason(expense) == null)")
    @Mapping(target = "voidBlockReason", expression = "java(voidBlockReason(expense))")
    ExpenseResponse toResponse(Expense expense);

    default ExpenseVoidBlockReason voidBlockReason(Expense expense) {
        if (expense.isVoided()) {
            return ExpenseVoidBlockReason.ALREADY_VOIDED;
        }
        if (expense.getCashSession().getStatus() == CashSessionStatus.CLOSED) {
            return ExpenseVoidBlockReason.CASH_SESSION_CLOSED;
        }
        return null;
    }
}
