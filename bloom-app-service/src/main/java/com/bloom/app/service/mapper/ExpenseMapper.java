package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.domain.model.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {
    @Mapping(target = "cashSessionId", source = "cashSession.id")
    @Mapping(target = "operationalExpense", expression = "java(expense.getCategory().isOperationalExpense())")
    ExpenseResponse toResponse(Expense expense);
}
