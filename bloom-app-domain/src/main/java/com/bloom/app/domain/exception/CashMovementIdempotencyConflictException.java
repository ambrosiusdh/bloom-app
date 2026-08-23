package com.bloom.app.domain.exception;

public class CashMovementIdempotencyConflictException extends RuntimeException {
    public CashMovementIdempotencyConflictException() {
        super("Idempotency key has already been used for a different cash movement");
    }
}
