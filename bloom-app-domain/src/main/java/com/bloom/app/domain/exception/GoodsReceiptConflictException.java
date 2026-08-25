package com.bloom.app.domain.exception;

public class GoodsReceiptConflictException extends RuntimeException {
    public GoodsReceiptConflictException(String message) {
        super(message);
    }
}
