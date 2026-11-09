package com.bloom.app.domain.exception;

import com.bloom.app.domain.error.ErrorCode;
import lombok.Getter;

@Getter
public class ItemSkuAlreadyExistsException extends BusinessException {
    private final String sku;

    public ItemSkuAlreadyExistsException(String sku) {
        super(ErrorCode.ITEM_SKU_ALREADY_EXISTS, sku);
        this.sku = sku;
    }

    public ItemSkuAlreadyExistsException(String sku, Throwable cause) {
        this(sku);
        initCause(cause);
    }
}
