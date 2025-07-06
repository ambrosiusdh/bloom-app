package com.bloom.app.domain.exception;

public class UserNotFoundException extends BaseException {
    public UserNotFoundException(String username) {
        super(String.format("User not found with username '%s'", username));
    }
}
