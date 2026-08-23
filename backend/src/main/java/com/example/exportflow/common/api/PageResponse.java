package com.example.exportflow.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {

    public PageResponse {
        items = List.copyOf(items);
    }
}

