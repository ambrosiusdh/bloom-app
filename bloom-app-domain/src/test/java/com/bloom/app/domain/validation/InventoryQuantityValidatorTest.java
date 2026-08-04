package com.bloom.app.domain.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryQuantityValidatorTest {
    @Test
    void acceptsWholeValuesAtAnyAllowedScaleForNonFractionalItems() {
        assertThatCode(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2"), false))
            .doesNotThrowAnyException();
        assertThatCode(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.0"), false))
            .doesNotThrowAnyException();
        assertThatCode(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.0000"), false))
            .doesNotThrowAnyException();
    }

    @Test
    void appliesTheItemFractionalQuantityRule() {
        assertThatCode(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.5000"), true))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.5000"), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Fractional quantity is not allowed for this item");
    }

    @Test
    void rejectsFifthDecimalPlaceEvenWhenItIsOnlyATrailingZero() {
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.50000"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity scale must not exceed four decimal places");
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("2.50001"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity scale must not exceed four decimal places");
    }

    @Test
    void rejectsMissingNonPositiveAndNegativeStockValues() {
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity is required");
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("0.0000"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
        assertThatThrownBy(() -> InventoryQuantityValidator.validateIncoming(new BigDecimal("-1"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
        assertThatThrownBy(() -> InventoryQuantityValidator.validateStock(new BigDecimal("-0.0001"), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Stock may not be negative");
    }
}
