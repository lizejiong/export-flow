package com.example.exportflow.export;

import com.example.exportflow.export.application.FilterSnapshot;
import com.example.exportflow.export.application.RequestHasher;
import com.example.exportflow.export.domain.ExportType;
import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.domain.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {
    private final RequestHasher hasher = new RequestHasher(new ObjectMapper().findAndRegisterModules());

    @Test
    void selectedIdsAreSortedAndDeduplicated() {
        var first = hasher.canonicalize(ExportType.SELECTED, List.of(3L, 1L, 3L), null);
        var second = hasher.canonicalize(ExportType.SELECTED, List.of(1L, 3L), null);
        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second));
    }

    @Test
    void filterEnumsAreCanonicalized() {
        OrderFilter first = new OrderFilter();
        first.setOrderStatuses(List.of(OrderStatus.SHIPPED, OrderStatus.PENDING_PAYMENT));
        OrderFilter second = new OrderFilter();
        second.setOrderStatuses(List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.SHIPPED));
        assertThat(hasher.hash(hasher.canonicalize(ExportType.FILTER, List.of(), FilterSnapshot.from(first))))
                .isEqualTo(hasher.hash(hasher.canonicalize(ExportType.FILTER, List.of(), FilterSnapshot.from(second))));
    }
}

