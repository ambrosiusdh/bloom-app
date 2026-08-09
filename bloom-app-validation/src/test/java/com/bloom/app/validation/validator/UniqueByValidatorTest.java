package com.bloom.app.validation.validator;

import com.bloom.app.validation.UniqueBy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UniqueByValidatorTest {
    @Test
    void rejectsNullElementsWithoutThrowingReflectionException() {
        UniqueBy annotation = mock(UniqueBy.class);
        when(annotation.property()).thenReturn("value");
        UniqueByValidator validator = new UniqueByValidator();
        validator.initialize(annotation);

        assertThat(validator.isValid(Arrays.asList(new Row("A"), null), null)).isFalse();
    }

    private record Row(String value) {
    }
}
