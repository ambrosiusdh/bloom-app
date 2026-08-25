package com.bloom.app.domain.exception;

public class ExpenseIdempotencyConflictException extends RuntimeException {
    public ExpenseIdempotencyConflictException() {
        super("Idempotency key has already been used for a different expense request");
    }
}
