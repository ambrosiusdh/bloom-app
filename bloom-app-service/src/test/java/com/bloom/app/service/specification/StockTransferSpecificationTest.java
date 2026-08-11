package com.bloom.app.service.specification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockTransferSpecificationTest {
    @Test
    void normalizesCaseAndWhitespaceFromGeneratedCode() {
        assertThat(StockTransferSpecification.normalizeCode(" st / viii-2026 / 0001 "))
            .isEqualTo("ST/VIII-2026/0001");
        assertThat(StockTransferSpecification.normalizeCode("  ")).isNull();
        assertThat(StockTransferSpecification.normalizeCode(null)).isNull();
    }
}
