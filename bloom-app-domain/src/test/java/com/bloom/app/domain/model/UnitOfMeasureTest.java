package com.bloom.app.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitOfMeasureTest {
    @Test
    void exposesStableCodes() {
        assertThat(UnitOfMeasure.values()).containsExactly(
            UnitOfMeasure.PIECE, UnitOfMeasure.KILOGRAM, UnitOfMeasure.GRAM,
            UnitOfMeasure.LITER, UnitOfMeasure.MILLILITER, UnitOfMeasure.METER,
            UnitOfMeasure.CENTIMETER, UnitOfMeasure.ROLL);
    }
}
