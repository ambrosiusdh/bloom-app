package com.bloom.app.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private int code;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message("Success")
            .code(200)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> fail(String message, int code) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .code(code)
            .data(null)
            .build();
    }
}
