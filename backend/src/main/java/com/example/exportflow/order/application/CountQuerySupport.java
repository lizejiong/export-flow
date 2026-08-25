package com.example.exportflow.order.application;

import com.example.exportflow.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.sql.SQLTimeoutException;

public final class CountQuerySupport {
    private CountQuerySupport() {
    }

    public static RuntimeException map(RuntimeException exception) {
        return map(exception, 5);
    }

    public static RuntimeException map(RuntimeException exception, int timeoutSeconds) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLTimeoutException || containsTimeout(current.getMessage())) {
                return new BusinessException("EXPORT_COUNT_TIMEOUT", HttpStatus.UNPROCESSABLE_ENTITY,
                        "统计超过 " + timeoutSeconds + " 秒，请缩小筛选范围");
            }
            current = current.getCause();
        }
        return exception;
    }

    private static boolean containsTimeout(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时");
    }
}
