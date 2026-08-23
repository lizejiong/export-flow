package com.example.exportflow.order;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.order.application.CountQuerySupport;
import org.junit.jupiter.api.Test;

import java.sql.SQLTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class CountQuerySupportTest {
    @Test
    void mapsNestedSqlTimeout() {
        RuntimeException mapped = CountQuerySupport.map(new RuntimeException(new SQLTimeoutException("query timeout")));
        assertThat(mapped).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) mapped).code()).isEqualTo("EXPORT_COUNT_TIMEOUT");
    }
}
