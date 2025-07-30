package com.ldsilver.chingoohaja.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private T data;
    private String message;
    private LocalDateTime timestamp;

    private ApiResponse(T data, String message) {
        this.data = data;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // === 성공 응답 (2xx) ==
    public static <T> ApiResponse<T> of (T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(null, null);
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(null, message);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(data, message);
    }

    // === 생성 응답 (201) ===
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(data, message);
    }
}
