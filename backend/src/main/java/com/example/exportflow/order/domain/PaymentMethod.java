package com.example.exportflow.order.domain;

public enum PaymentMethod {
    ALIPAY("支付宝"), WECHAT("微信"), BANK_CARD("银行卡");
    private final String label;
    PaymentMethod(String label) { this.label = label; }
    public String label() { return label; }
}

