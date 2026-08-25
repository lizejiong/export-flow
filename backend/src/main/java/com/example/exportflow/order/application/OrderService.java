package com.example.exportflow.order.application;

import com.example.exportflow.common.api.PageResponse;
import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.order.domain.Order;
import com.example.exportflow.order.infrastructure.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderMapper orderMapper;
    private final ExportProperties properties;

    public OrderService(OrderMapper orderMapper, ExportProperties properties) {
        this.orderMapper = orderMapper;
        this.properties = properties;
    }

    public PageResponse<Order> findPage(OrderFilter rawFilter, int page, int pageSize) {
        validatePage(page, pageSize);
        OrderFilter filter = rawFilter.normalized();
        long total = orderMapper.count(filter, null);
        long offset = ((long) page - 1) * pageSize;
        List<Order> items = orderMapper.findPage(filter, null, offset, pageSize);
        return new PageResponse<>(items, page, pageSize, total);
    }

    public ExportCount countForPreview(OrderFilter rawFilter, long limit) {
        OrderFilter filter = rawFilter.normalized();
        Long maxId = orderMapper.findMaxId();
        if (maxId == null) return new ExportCount(0, false);
        long count;
        try {
            count = orderMapper.count(filter, maxId);
        } catch (RuntimeException exception) {
            throw CountQuerySupport.map(exception, properties.countTimeoutSeconds());
        }
        return new ExportCount(count, count > limit);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || page > 10_000 || (pageSize != 20 && pageSize != 50 && pageSize != 100)) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "页码必须在 1 到 10000 之间，分页大小只能是 20、50 或 100");
        }
    }

    public record ExportCount(long count, boolean limitExceeded) {
    }
}
