package com.bloom.app.service.specification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementSpecificationTest {
    @Test
    void escapesLikeWildcardsForLiteralReferenceSearch() {
        assertThat(StockMovementSpecification.escapeLike("sale_50%\\final"))
            .isEqualTo("sale\\_50\\%\\\\final");
    }
}
