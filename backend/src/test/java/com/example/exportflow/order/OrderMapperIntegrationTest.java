package com.example.exportflow.order;

import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.domain.Order;
import com.example.exportflow.order.infrastructure.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Sql(scripts = "/order-mapper-schema.sql")
class OrderMapperIntegrationTest {

    @Autowired
    private OrderMapper mapper;

    @Test
    void mapsPrimitiveRecordConstructorAndOptionalSnapshotBoundary() {
        List<Order> orders = mapper.findPage(new OrderFilter().normalized(), null, 0, 20);

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.id()).isPositive();
            assertThat(order.itemCount()).isEqualTo(2);
            assertThat(order.orderNo()).isEqualTo("ORD-TEST-1");
        });
    }

    @Test
    void selectedExportQueryQualifiesColumnsSharedByJoinedTables() {
        assertThat(mapper.findSelectedBatch(77L, 0L, 100))
                .singleElement()
                .extracting(Order::orderNo)
                .isEqualTo("ORD-TEST-1");
    }
}
