package com.bloom.app.web.security;

public class SessionIdentityException extends RuntimeException {
    public SessionIdentityException() {
        super("Session identity expired; sign in again");
    }
}
