package com.bloom.app.api.exception;

import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.BaseUnitOfMeasureImmutableException;
import com.bloom.app.domain.exception.FractionalQuantityPolicyImmutableException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.exception.IdempotencyConflictException;
import com.bloom.app.domain.exception.CashMovementIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(HttpServletRequest ignoredRequest, MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> error = new HashMap<>();
            error.put("field", fieldError.getField());
            error.put("message", fieldError.getDefaultMessage());
            errors.add(error);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorType", "ValidationFailed");
        body.put("message", errors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "Malformed JSON request or unsupported enum value");
        body.put("code", HttpStatus.BAD_REQUEST.value());
        body.put("errorType", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getReason());
        body.put("code", ex.getStatusCode().value());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("code", ex.getErrorCode().getCode());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("code", HttpStatus.NOT_FOUND.value());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("code", HttpStatus.BAD_REQUEST.value());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<?> handleInsufficientStock(InsufficientStockException ex) {
        return domainError(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
        StockConcurrencyException.class,
        BaseUnitOfMeasureImmutableException.class,
        FractionalQuantityPolicyImmutableException.class,
        IdempotencyConflictException.class,
        CashMovementIdempotencyConflictException.class,
        CheckoutIdempotencyConflictException.class,
        CashSessionConflictException.class
    })
    public ResponseEntity<?> handleDomainConflict(RuntimeException ex) {
        return domainError(ex, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(AuthenticationException ex) {
        return domainError(ex, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex) {
        return domainError(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "The resource was modified by another transaction. Reload and retry.");
        body.put("code", HttpStatus.CONFLICT.value());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(HttpServletRequest request, Exception ex) {
        log.error("Unhandled exception caught at endpoint {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<?> domainError(RuntimeException exception, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", exception.getMessage());
        body.put("code", status.value());
        body.put("errorType", exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body(body);
    }
}
