package com.bloom.app.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentType {
    GOODS_RECEIPT("GOODS_RECEIPT", "GR"),
    SALE("SALE", "SALE"),
    STOCK_ADJUSTMENT("STOCK_ADJUSTMENT", "SA"),
    STOCK_TRANSFER("STOCK_TRANSFER", "ST");

    private final String documentCode;
    private final String documentPrefix;
}
