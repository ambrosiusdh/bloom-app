package com.bloom.app.domain.exception;

public class StockConcurrencyException extends RuntimeException {
    public StockConcurrencyException(String sku, Throwable cause) {
        super("Stock for item " + sku + " was modified concurrently. Reload and retry.", cause);
    }
}
