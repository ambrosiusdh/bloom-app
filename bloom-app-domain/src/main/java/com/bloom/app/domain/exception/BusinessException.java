package com.bloom.app.domain.exception;

import com.bloom.app.domain.error.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(args.length > 0 ? String.format(errorCode.getMessage(), args) : errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
