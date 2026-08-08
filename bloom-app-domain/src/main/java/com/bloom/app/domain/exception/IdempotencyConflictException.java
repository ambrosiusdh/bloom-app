package com.bloom.app.domain.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key has already been used for a different stock transfer request");
    }
}
