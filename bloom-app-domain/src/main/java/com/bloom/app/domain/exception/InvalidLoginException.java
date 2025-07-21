package com.bloom.app.domain.exception;

public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException() {
        super("Username or password is invalid");
    }
}
