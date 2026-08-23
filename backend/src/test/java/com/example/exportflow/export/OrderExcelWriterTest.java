package com.example.exportflow.export;

import com.example.exportflow.export.infrastructure.excel.OrderExcelWriter;
import com.example.exportflow.order.domain.*;
import com.example.exportflow.support.TestExportProperties;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExcelWriterTest {
    @TempDir Path tempDir;

    @Test
    void writesSafeStreamingWorkbook() throws Exception {
        OrderExcelWriter writer = new OrderExcelWriter(TestExportProperties.create(tempDir));
        Path file = tempDir.resolve("orders.xlsx");
        List<Order> orders = List.of(order(1, "张三"), order(2, "=1+1"));

        long count = writer.write(file, afterId -> afterId == 0 ? orders : List.of(), ignored -> {});

        assertThat(count).isEqualTo(2);
        assertThat(Files.size(file)).isGreaterThan(0);
        try (var workbook = WorkbookFactory.create(file.toFile())) {
            var sheet = workbook.getSheet("订单数据");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 14);
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("'=1+1");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    private Order order(long id, String name) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 10, 30);
        return new Order(id, "ORD" + id, name, "13800138000", OrderStatus.COMPLETED,
                PaymentStatus.PAID, PaymentMethod.ALIPAY, OrderSource.WEB, 2,
                new BigDecimal("123.45"), "上海", now, now.plusMinutes(1), now.plusHours(1));
    }
}

