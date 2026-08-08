package com.bloom.app.domain.exception;

import com.bloom.app.domain.enums.StockLocation;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, StockLocation stockLocation) {
        super("Insufficient stock in " + stockLocation + " for item: " + sku);
    }
}
