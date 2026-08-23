package com.example.exportflow.export.application;

import com.example.exportflow.common.error.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ExportFailureClassifier {
    public Failure classify(Throwable throwable) {
        if (throwable instanceof BusinessException business) {
            return new Failure(business.code(), business.getMessage(), false);
        }
        if (throwable instanceof IllegalArgumentException) {
            return new Failure("EXPORT_DATA_ERROR", message(throwable), false);
        }
        return new Failure("EXPORT_SYSTEM_ERROR", message(throwable), true);
    }

    private String message(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record Failure(String code, String message, boolean retryable) {}
}

