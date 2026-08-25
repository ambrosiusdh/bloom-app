package com.bloom.app.domain.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    GOODS_RECEIPT_NOT_FOUND(ErrorCodeConstants.GOODS_RECEIPT_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.GOODS_RECEIPT_NOT_FOUND_MESSAGE),

    SUPPLIER_NOT_FOUND(ErrorCodeConstants.SUPPLIER_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.SUPPLIER_NOT_FOUND_MESSAGE),
    SUPPLIER_CODE_ALREADY_EXISTS(ErrorCodeConstants.SUPPLIER_CODE_ALREADY_EXISTS_CODE, HttpStatus.CONFLICT, ErrorCodeConstants.SUPPLIER_CODE_ALREADY_EXISTS_MESSAGE),
    SUPPLIER_HAS_FINANCIAL_HISTORY(ErrorCodeConstants.SUPPLIER_HAS_FINANCIAL_HISTORY_CODE, HttpStatus.CONFLICT, ErrorCodeConstants.SUPPLIER_HAS_FINANCIAL_HISTORY_MESSAGE),

    ITEM_NOT_FOUND(ErrorCodeConstants.ITEM_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.ITEM_NOT_FOUND_MESSAGE),
    ITEM_QUANTITY_MUST_BE_POSITIVE(ErrorCodeConstants.ITEM_QUANTITY_MUST_BE_POSITIVE_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.ITEM_QUANTITY_MUST_BE_POSITIVE_MESSAGE),

    ITEM_CATEGORY_ALREADY_EXIST(ErrorCodeConstants.ITEM_CATEGORY_ALREADY_EXISTS_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.ITEM_CATEGORY_ALREADY_EXISTS_MESSAGE),
    ITEM_CATEGORY_NOT_FOUND(ErrorCodeConstants.ITEM_CATEGORY_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.ITEM_CATEGORY_NOT_FOUND_MESSAGE),

    PRINTER_NOT_FOUND(ErrorCodeConstants.PRINTER_NOT_FOUND_CODE, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeConstants.PRINTER_NOT_FOUND_MESSAGE),

    SALE_NOT_FOUND(ErrorCodeConstants.SALE_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.SALE_NOT_FOUND_MESSAGE),
    SALE_INSUFFICIENT_STOCK_STORE(ErrorCodeConstants.SALE_INSUFFICIENT_STOCK_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.SALE_INSUFFICIENT_STOCK_MESSAGE),
    SALE_PAID_LESS_THAN_TOTAL(ErrorCodeConstants.SALE_PAID_LESS_THAN_TOTAL_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.SALE_PAID_LESS_THAN_TOTAL_MESSAGE),
    SALE_QRIS_PAYMENT_MISMATCH(ErrorCodeConstants.SALE_QRIS_PAYMENT_MISMATCH_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.SALE_QRIS_PAYMENT_MISMATCH_MESSAGE),;

    private final String code;
    private final HttpStatus status;
    private final String message;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
