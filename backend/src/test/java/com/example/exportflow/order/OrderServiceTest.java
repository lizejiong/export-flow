package com.example.exportflow.order;

import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.application.OrderService;
import com.example.exportflow.order.infrastructure.OrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void pageQuerySuppliesNullSnapshotBoundaryToSharedFilterSql() {
        OrderMapper mapper = mock(OrderMapper.class);
        when(mapper.count(any(), isNull())).thenReturn(0L);
        when(mapper.findPage(any(), isNull(), any(Integer.class), any(Integer.class))).thenReturn(List.of());

        new OrderService(mapper).findPage(new OrderFilter(), 1, 20);

        verify(mapper).findPage(any(), isNull(), any(Integer.class), any(Integer.class));
    }
}
