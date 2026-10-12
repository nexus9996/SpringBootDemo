package com.example.demo.enums;

/**
 * 用户状态。
 */
public enum UserStatus {

    ACTIVE("启用"),
    DISABLED("禁用"),
    LOCKED("锁定");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
