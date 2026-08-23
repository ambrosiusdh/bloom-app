package com.bloom.app.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.exception.IdempotencyConflictException;
import com.bloom.app.domain.exception.FractionalQuantityPolicyImmutableException;

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

    @Test
    void returnsConflictForStockDomainConcurrencyException() {
        StockConcurrencyException exception = new StockConcurrencyException(
            "ITEM-1", new ObjectOptimisticLockingFailureException(Object.class, 42L));

        var response = handler.handleInventoryConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("errorType")).isEqualTo("StockConcurrencyException");
        assertThat(body.get("message")).isEqualTo(
            "Stock for item ITEM-1 was modified concurrently. Reload and retry.");
    }

    @Test
    void returnsConflictForReusedIdempotencyKeyWithDifferentPayload() {
        var response = handler.handleInventoryConflict(new IdempotencyConflictException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.get("errorType")).isEqualTo("IdempotencyConflictException");
        assertThat(body.get("message")).isEqualTo(
            "Idempotency key has already been used for a different stock transfer request");
    }

    @Test
    void returnsConflictForFractionalQuantityPolicyChangeAfterMovement() {
        var response = handler.handleInventoryConflict(
            new FractionalQuantityPolicyImmutableException("ITEM-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.get("errorType"))
            .isEqualTo("FractionalQuantityPolicyImmutableException");
        assertThat(body.get("message")).isEqualTo(
            "Fractional quantity policy cannot change after the first stock movement for item: ITEM-1");
    }

    @Test
    void returnsBadRequestForIllegalArgumentException() {
        var response = handler.handleIllegalArgumentException(
            new IllegalArgumentException("Source and destination locations must differ"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("errorType")).isEqualTo("IllegalArgumentException");
    }
}
