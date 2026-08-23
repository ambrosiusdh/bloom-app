package com.bloom.app.service.support;

public final class CashSessionLockConstants {
    /** Stable PostgreSQL advisory-lock namespace for the one-register transition. */
    public static final long GLOBAL_SESSION_TRANSITION_LOCK_ID = 67_294_367_138_521L;

    private CashSessionLockConstants() {
    }
}
