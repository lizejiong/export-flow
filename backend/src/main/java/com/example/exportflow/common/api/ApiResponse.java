package com.example.exportflow.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, requestId, OffsetDateTime.now(ZONE));
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data, String requestId) {
        return new ApiResponse<>(code, message, data, requestId, OffsetDateTime.now(ZONE));
    }
}
