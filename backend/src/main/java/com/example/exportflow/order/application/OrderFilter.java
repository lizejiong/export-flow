package com.example.exportflow.order.application;

import com.example.exportflow.common.error.BusinessException;
import com.example.exportflow.order.domain.OrderSource;
import com.example.exportflow.order.domain.OrderStatus;
import com.example.exportflow.order.domain.PaymentMethod;
import com.example.exportflow.order.domain.PaymentStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderFilter {
    private String orderNo;
    private String customerName;
    private String customerPhone;
    private List<OrderStatus> orderStatuses = List.of();
    private List<PaymentStatus> paymentStatuses = List.of();
    private List<PaymentMethod> paymentMethods = List.of();
    private List<OrderSource> orderSources = List.of();
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;

    public OrderFilter normalized() {
        OrderFilter result = new OrderFilter();
        result.orderNo = trimToNull(orderNo);
        result.customerName = trimToNull(customerName);
        result.customerPhone = trimToNull(customerPhone);
        result.orderStatuses = orderStatuses == null ? List.of() : List.copyOf(orderStatuses);
        result.paymentStatuses = paymentStatuses == null ? List.of() : List.copyOf(paymentStatuses);
        result.paymentMethods = paymentMethods == null ? List.of() : List.copyOf(paymentMethods);
        result.orderSources = orderSources == null ? List.of() : List.copyOf(orderSources);
        result.minAmount = minAmount;
        result.maxAmount = maxAmount;
        result.createdFrom = createdFrom;
        result.createdTo = createdTo;
        result.validate();
        return result;
    }

    private void validate() {
        if (minAmount != null && minAmount.signum() < 0) {
            throw validation("最小金额不能小于 0");
        }
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw validation("最大金额不能小于 0");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw validation("最大金额不能小于最小金额");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw validation("结束时间不能早于开始时间");
        }
        if (length(orderNo) > 32 || length(customerPhone) > 32 || length(customerName) > 100) {
            throw validation("筛选文本长度超出限制");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
    }

    private int length(String value) { return value == null ? 0 : value.trim().length(); }
    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    public String getCustomerNameLike() {
        if (customerName == null) return null;
        return "%" + customerName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public boolean isEmpty() {
        return orderNo == null && customerName == null && customerPhone == null
                && orderStatuses.isEmpty() && paymentStatuses.isEmpty() && paymentMethods.isEmpty()
                && orderSources.isEmpty() && minAmount == null && maxAmount == null
                && createdFrom == null && createdTo == null;
    }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public List<OrderStatus> getOrderStatuses() { return orderStatuses; }
    public void setOrderStatuses(List<OrderStatus> orderStatuses) { this.orderStatuses = orderStatuses; }
    public List<PaymentStatus> getPaymentStatuses() { return paymentStatuses; }
    public void setPaymentStatuses(List<PaymentStatus> paymentStatuses) { this.paymentStatuses = paymentStatuses; }
    public List<PaymentMethod> getPaymentMethods() { return paymentMethods; }
    public void setPaymentMethods(List<PaymentMethod> paymentMethods) { this.paymentMethods = paymentMethods; }
    public List<OrderSource> getOrderSources() { return orderSources; }
    public void setOrderSources(List<OrderSource> orderSources) { this.orderSources = orderSources; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    public LocalDateTime getCreatedFrom() { return createdFrom; }
    public void setCreatedFrom(LocalDateTime createdFrom) { this.createdFrom = createdFrom; }
    public LocalDateTime getCreatedTo() { return createdTo; }
    public void setCreatedTo(LocalDateTime createdTo) { this.createdTo = createdTo; }
}
