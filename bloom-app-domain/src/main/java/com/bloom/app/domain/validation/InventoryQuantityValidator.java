package com.bloom.app.domain.validation;

import java.math.BigDecimal;

/** Central validation for quantities represented in an item's base unit of measure. */
public final class InventoryQuantityValidator {
    public static final int MAX_SCALE = 4;

    private InventoryQuantityValidator() {
    }

    public static void validateIncoming(BigDecimal quantity, boolean fractionalQuantityAllowed) {
        validateRequiredAndScale(quantity);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        validateFractionalRule(quantity, fractionalQuantityAllowed);
    }

    public static void validateIncoming(BigDecimal quantity) {
        validateIncoming(quantity, true);
    }

    public static void validateStock(BigDecimal stock, boolean fractionalQuantityAllowed) {
        validateRequiredAndScale(stock);
        if (stock.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Stock may not be negative");
        }
        validateFractionalRule(stock, fractionalQuantityAllowed);
    }

    private static void validateRequiredAndScale(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity is required");
        }
        if (quantity.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("Quantity scale must not exceed four decimal places");
        }
    }

    private static void validateFractionalRule(BigDecimal quantity, boolean fractionalQuantityAllowed) {
        if (!fractionalQuantityAllowed && quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Fractional quantity is not allowed for this item");
        }
    }
}
