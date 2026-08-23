package com.bloom.app.domain.enums;

public enum CashMovementType {
    SALE_PAYMENT(CashMovementSourceType.SALE, CashMovementDirection.IN),
    SUPPLIER_PAYMENT(CashMovementSourceType.SUPPLIER_PAYMENT, CashMovementDirection.OUT),
    EXPENSE(CashMovementSourceType.EXPENSE, CashMovementDirection.OUT),
    EXPENSE_REVERSAL(CashMovementSourceType.EXPENSE, CashMovementDirection.IN);

    private final CashMovementSourceType sourceType;
    private final CashMovementDirection direction;

    CashMovementType(CashMovementSourceType sourceType, CashMovementDirection direction) {
        this.sourceType = sourceType;
        this.direction = direction;
    }

    public CashMovementSourceType sourceType() {
        return sourceType;
    }

    public CashMovementDirection direction() {
        return direction;
    }
}
