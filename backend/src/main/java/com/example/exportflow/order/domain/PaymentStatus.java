package com.example.exportflow.order.domain;

public enum PaymentStatus {
    UNPAID("未支付"), PAID("已支付"), REFUNDED("已退款");
    private final String label;
    PaymentStatus(String label) { this.label = label; }
    public String label() { return label; }
}

