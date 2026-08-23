package com.example.exportflow.export.application;

import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.domain.OrderSource;
import com.example.exportflow.order.domain.OrderStatus;
import com.example.exportflow.order.domain.PaymentMethod;
import com.example.exportflow.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record FilterSnapshot(
        String orderNo,
        String customerName,
        String customerPhone,
        List<OrderStatus> orderStatuses,
        List<PaymentStatus> paymentStatuses,
        List<PaymentMethod> paymentMethods,
        List<OrderSource> orderSources,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
    public static FilterSnapshot from(OrderFilter raw) {
        OrderFilter filter = raw.normalized();
        return new FilterSnapshot(
                filter.getOrderNo(), filter.getCustomerName(), filter.getCustomerPhone(),
                sorted(filter.getOrderStatuses()), sorted(filter.getPaymentStatuses()),
                sorted(filter.getPaymentMethods()), sorted(filter.getOrderSources()),
                filter.getMinAmount(), filter.getMaxAmount(), filter.getCreatedFrom(), filter.getCreatedTo()
        );
    }

    private static <E extends Enum<E>> List<E> sorted(List<E> values) {
        return values.stream().sorted(Comparator.comparing(Enum::name)).toList();
    }

    public OrderFilter toFilter() {
        OrderFilter filter = new OrderFilter();
        filter.setOrderNo(orderNo);
        filter.setCustomerName(customerName);
        filter.setCustomerPhone(customerPhone);
        filter.setOrderStatuses(orderStatuses);
        filter.setPaymentStatuses(paymentStatuses);
        filter.setPaymentMethods(paymentMethods);
        filter.setOrderSources(orderSources);
        filter.setMinAmount(minAmount);
        filter.setMaxAmount(maxAmount);
        filter.setCreatedFrom(createdFrom);
        filter.setCreatedTo(createdTo);
        return filter.normalized();
    }
}

