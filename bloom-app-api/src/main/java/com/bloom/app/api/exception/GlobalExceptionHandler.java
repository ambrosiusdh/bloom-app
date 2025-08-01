package com.bloom.app.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
    public ResponseEntity<?> handleValidationErrors(HttpServletRequest request, MethodArgumentNotValidException ex) throws MethodArgumentNotValidException {
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getReason());
        body.put("code", ex.getStatusCode().value());
        body.put("errorType", ex.getClass().getSimpleName());

        return ResponseEntity.status(ex.getStatusCode()).body(body);
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
}
