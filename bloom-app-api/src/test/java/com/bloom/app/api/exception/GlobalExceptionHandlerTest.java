package com.bloom.app.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsConflictForOptimisticLockingFailure() {
        ObjectOptimisticLockingFailureException exception =
            new ObjectOptimisticLockingFailureException(Object.class, 42L);

        var response = handler.handleOptimisticLockingFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("code")).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.get("errorType")).isEqualTo("ObjectOptimisticLockingFailureException");
        assertThat(body.get("message")).isEqualTo(
            "The resource was modified by another transaction. Reload and retry."
        );
    }
}
