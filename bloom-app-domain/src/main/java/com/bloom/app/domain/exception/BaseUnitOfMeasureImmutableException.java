package com.bloom.app.domain.exception;

public class BaseUnitOfMeasureImmutableException extends RuntimeException {
    public BaseUnitOfMeasureImmutableException(String sku) {
        super("Base unit of measure cannot change after the first stock movement for item: " + sku);
    }
}
