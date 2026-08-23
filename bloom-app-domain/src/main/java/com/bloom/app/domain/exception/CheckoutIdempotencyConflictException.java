package com.bloom.app.domain.exception;

public class CheckoutIdempotencyConflictException extends RuntimeException {
    public CheckoutIdempotencyConflictException() {
        super("Idempotency key has already been used for a different checkout request");
    }
}
