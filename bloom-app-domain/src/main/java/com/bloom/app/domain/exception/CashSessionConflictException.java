package com.bloom.app.domain.exception;

public class CashSessionConflictException extends RuntimeException {
    public CashSessionConflictException(String message) {
        super(message);
    }

    public CashSessionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
