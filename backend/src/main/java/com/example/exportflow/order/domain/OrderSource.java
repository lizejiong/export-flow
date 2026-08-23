package com.example.exportflow.order.domain;

public enum OrderSource {
    WEB("Web"), APP("App"), MINI_PROGRAM("小程序");
    private final String label;
    OrderSource(String label) { this.label = label; }
    public String label() { return label; }
}

