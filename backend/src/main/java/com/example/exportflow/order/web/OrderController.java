package com.example.exportflow.order.web;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.application.OrderService;
import com.example.exportflow.order.domain.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final ExportProperties properties;

    public OrderController(OrderService orderService, ExportProperties properties) {
        this.orderService = orderService;
        this.properties = properties;
    }

    @GetMapping
    public PageResponse<Order> findPage(
            OrderFilter filter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return orderService.findPage(filter, page, pageSize);
    }

    @GetMapping("/export-count")
    public OrderService.ExportCount count(OrderFilter filter) {
        return orderService.countForPreview(filter, properties.maxFilterRows());
    }
}

