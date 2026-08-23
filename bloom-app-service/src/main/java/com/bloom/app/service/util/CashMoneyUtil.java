package com.bloom.app.service.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CashMoneyUtil {
    public static final int SCALE = 4;
    private static final int MAX_PRECISION = 19;

    private CashMoneyUtil() {
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        validateShape(value, fieldName);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        validateShape(value, fieldName);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal reconciliationBoundary(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static void validateShape(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(fieldName + " must have at most 4 decimal places");
        }
        BigDecimal normalized = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (normalized.precision() > MAX_PRECISION) {
            throw new IllegalArgumentException(fieldName + " exceeds NUMERIC(19,4)");
        }
    }
}
