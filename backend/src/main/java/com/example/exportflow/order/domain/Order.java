package com.example.exportflow.order.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        long id,
        String orderNo,
        String customerName,
        String customerPhone,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        OrderSource orderSource,
        int itemCount,
        BigDecimal totalAmount,
        String shippingProvince,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime updatedAt
) {
}

