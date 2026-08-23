package com.example.exportflow.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT("待付款"),
    PENDING_SHIPMENT("待发货"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CLOSED("已关闭");

    private final String label;

    OrderStatus(String label) { this.label = label; }
    public String label() { return label; }
}

