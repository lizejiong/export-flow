package com.example.exportflow.common.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public BusinessException(String code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public BusinessException(String code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }
}

