package com.bloom.app.domain.exception;

public class SupplierPaymentConflictException extends RuntimeException {
    public SupplierPaymentConflictException(String message) {
        super(message);
    }
}
