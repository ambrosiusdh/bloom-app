package com.bloom.app.domain.enums;

public enum ExpenseCategory {
    STORE_OPERATIONAL,
    FOOD_AND_DRINK,
    CHARITY,
    EMERGENCY_PURCHASE,
    OWNER_WITHDRAWAL,
    OTHER;

    public boolean isOperationalExpense() {
        return this != OWNER_WITHDRAWAL;
    }
}
