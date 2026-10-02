package com.bloom.app.api.helper;

import com.bloom.app.api.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseHelper {
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.ok(message, data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String msg, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, msg, 201, data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> accepted(String msg, T data) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ApiResponse<>(true, msg, HttpStatus.ACCEPTED.value(), data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, 400));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unauthorizedRequest(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(message, 401));
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(message, 404));
    }

    public static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.fail(message, HttpStatus.CONFLICT.value()));
    }

    public static <T> ResponseEntity<ApiResponse<T>> serviceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.fail(message, HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(message, 500));
    }
}
