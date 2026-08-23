package com.example.exportflow.order.application;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.order.domain.Order;
import com.example.exportflow.order.infrastructure.OrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public PageResponse<Order> findPage(OrderFilter rawFilter, int page, int pageSize) {
        OrderFilter filter = rawFilter.normalized();
        int safePage = Math.max(1, page);
        int safeSize = switch (pageSize) { case 20, 50, 100 -> pageSize; default -> 20; };
        long total = orderMapper.count(filter, null);
        List<Order> items = orderMapper.findPage(filter, null, (safePage - 1) * safeSize, safeSize);
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    public ExportCount countForPreview(OrderFilter rawFilter, long limit) {
        OrderFilter filter = rawFilter.normalized();
        Long maxId = orderMapper.findMaxId();
        if (maxId == null) return new ExportCount(0, false);
        long count;
        try { count = orderMapper.count(filter, maxId); }
        catch (RuntimeException exception) { throw CountQuerySupport.map(exception); }
        return new ExportCount(count, count > limit);
    }

    public record ExportCount(long count, boolean limitExceeded) {}
}
