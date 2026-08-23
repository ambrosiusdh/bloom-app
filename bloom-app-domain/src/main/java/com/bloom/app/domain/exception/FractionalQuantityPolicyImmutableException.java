package com.bloom.app.domain.exception;

public class FractionalQuantityPolicyImmutableException extends RuntimeException {
    public FractionalQuantityPolicyImmutableException(String sku) {
        super("Fractional quantity policy cannot change after the first stock movement for item: " + sku);
    }
}
