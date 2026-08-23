package com.example.exportflow.common.error;

import com.example.exportflow.common.api.ApiResponse;
import com.example.exportflow.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Map<String, Object>>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), exception.details(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiResponse<Map<String, Object>>> handleBinding(Exception exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException method) {
            method.getBindingResult().getFieldErrors()
                    .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        } else if (exception instanceof BindException bind) {
            bind.getBindingResult().getFieldErrors()
                    .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数校验失败", details, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Map<String, Object>>> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数格式错误", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Map<String, Object>>> handleUnknown(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误", Map.of(), request);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String requestId = attribute == null ? "unknown" : attribute.toString();
        ApiResponse<Map<String, Object>> body = ApiResponse.failure(code, message, details, requestId);
        return ResponseEntity.status(status).body(body);
    }
}
