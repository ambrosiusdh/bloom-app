package com.bloom.app.domain.exception;

public class SupplierPaymentIdempotencyConflictException extends RuntimeException {
    public SupplierPaymentIdempotencyConflictException() {
        super("Idempotency key has already been used for a different supplier payment request");
    }
}
