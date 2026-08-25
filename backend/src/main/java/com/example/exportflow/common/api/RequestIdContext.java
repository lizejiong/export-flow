package com.example.exportflow.common.api;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestIdContext {
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrCreate() {
        String requestId = current();
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
