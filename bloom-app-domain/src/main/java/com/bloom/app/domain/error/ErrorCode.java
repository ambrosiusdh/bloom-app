package com.bloom.app.domain.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    SALE_NOT_FOUND(ErrorCodeConstants.SALE_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.SALE_NOT_FOUND_MESSAGE),
    SALE_PAID_LESS_THAN_TOTAL(ErrorCodeConstants.SALE_PAID_LESS_THAN_TOTAL_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.SALE_PAID_LESS_THAN_TOTAL_MESSAGE),

    ITEM_CATEGORY_ALREADY_EXIST(ErrorCodeConstants.ITEM_CATEGORY_ALREADY_EXISTS_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.ITEM_CATEGORY_ALREADY_EXISTS_MESSAGE),
    ITEM_CATEGORY_NOT_FOUND(ErrorCodeConstants.ITEM_CATEGORY_NOT_FOUND_CODE, HttpStatus.NOT_FOUND, ErrorCodeConstants.ITEM_CATEGORY_NOT_FOUND_MESSAGE),
    ITEM_CATEGORY_HAS_ACTIVE_ITEM(ErrorCodeConstants.ITEM_CATEGORY_HAS_ACTIVE_ITEM_CODE, HttpStatus.BAD_REQUEST, ErrorCodeConstants.ITEM_CATEGORY_HAS_ACTIVE_ITEM_MESSAGE),

    PRINTER_NOT_FOUND(ErrorCodeConstants.PRINTER_NOT_FOUND_CODE, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeConstants.PRINTER_NOT_FOUND_MESSAGE),;

    private final String code;
    private final HttpStatus status;
    private final String message;
}
