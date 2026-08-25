package com.bloom.app.domain.exception;

public class GoodsReceiptIdempotencyConflictException extends RuntimeException {
    public GoodsReceiptIdempotencyConflictException() {
        super("Idempotency key has already been used for a different goods receipt request");
    }
}
