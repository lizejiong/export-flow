package com.example.exportflow.export.infrastructure.excel;

import com.example.exportflow.common.config.ExportProperties;
import com.example.exportflow.order.domain.Order;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.LongConsumer;

@Component
public class OrderExcelWriter {
    private static final String[] HEADERS = {
            "序号", "订单号", "客户姓名", "客户手机号", "订单状态", "支付状态", "支付方式",
            "订单来源", "商品数量", "订单金额", "收货省份", "创建时间", "支付时间", "更新时间"
    };
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ExportProperties properties;

    public OrderExcelWriter(ExportProperties properties) {
        this.properties = properties;
    }

    public long write(Path path, BatchLoader loader, LongConsumer progressCallback) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(properties.sxssfRowWindow());
        workbook.setCompressTempFiles(true);
        try (workbook; OutputStream output = Files.newOutputStream(path)) {
            Sheet sheet = workbook.createSheet("订单数据");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            sheet.setColumnWidth(1, 22 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            sheet.setColumnWidth(3, 16 * 256);
            for (int i = 11; i <= 13; i++) sheet.setColumnWidth(i, 21 * 256);

            long afterId = 0;
            long written = 0;
            while (true) {
                List<Order> batch = loader.load(afterId);
                if (batch.isEmpty()) break;
                for (Order order : batch) {
                    Row row = sheet.createRow(Math.toIntExact(written + 1));
                    writeRow(row, order, written + 1, moneyStyle);
                    written++;
                    afterId = order.id();
                }
                progressCallback.accept(written);
                if (batch.size() < properties.queryBatchSize()) break;
            }
            workbook.write(output);
            return written;
        } finally {
            workbook.dispose();
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeRow(Row row, Order order, long sequence, CellStyle moneyStyle) {
        row.createCell(0).setCellValue(sequence);
        text(row, 1, order.orderNo());
        text(row, 2, order.customerName());
        text(row, 3, order.customerPhone());
        text(row, 4, order.orderStatus().label());
        text(row, 5, order.paymentStatus().label());
        text(row, 6, order.paymentMethod().label());
        text(row, 7, order.orderSource().label());
        row.createCell(8).setCellValue(order.itemCount());
        Cell money = row.createCell(9);
        money.setCellValue(order.totalAmount().doubleValue());
        money.setCellStyle(moneyStyle);
        text(row, 10, order.shippingProvince());
        text(row, 11, DATE_TIME.format(order.createdAt()));
        text(row, 12, order.paidAt() == null ? "" : DATE_TIME.format(order.paidAt()));
        text(row, 13, DATE_TIME.format(order.updatedAt()));
    }

    private void text(Row row, int column, String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        row.createCell(column, CellType.STRING).setCellValue(safe);
    }

    @FunctionalInterface
    public interface BatchLoader {
        List<Order> load(long afterId);
    }
}

