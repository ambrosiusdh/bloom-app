package com.bloom.app.domain.validation;

import java.util.Locale;

public final class SupplierCodePolicy {
    public static final int MAX_LENGTH = 255;

    private SupplierCodePolicy() {
    }

    public static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    public static String normalizeOptional(String code) {
        return code == null || code.isBlank() ? null : normalize(code);
    }
}
