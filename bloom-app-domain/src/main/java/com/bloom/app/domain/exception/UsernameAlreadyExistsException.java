package com.bloom.app.domain.exception;

public class UsernameAlreadyExistsException extends BaseException {
    public UsernameAlreadyExistsException(String username) {
        super(String.format("Username '%s' already exists", username));
    }
}
