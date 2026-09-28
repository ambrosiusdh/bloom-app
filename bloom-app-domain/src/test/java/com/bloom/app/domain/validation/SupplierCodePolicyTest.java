package com.bloom.app.domain.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierCodePolicyTest {
    @Test
    void canonicalizesCaseAndAsciiOrUnicodeBoundaryWhitespace() {
        assertThat(SupplierCodePolicy.normalize("  sup-001  ")).isEqualTo("SUP-001");
        assertThat(SupplierCodePolicy.normalize("\u2003sup-001\u2003"))
            .isEqualTo("SUP-001");
    }

    @Test
    void optionalNormalizationTreatsNullAndBlankAsAbsent() {
        assertThat(SupplierCodePolicy.normalizeOptional(null)).isNull();
        assertThat(SupplierCodePolicy.normalizeOptional(" \u2003 ")).isNull();
    }
}
