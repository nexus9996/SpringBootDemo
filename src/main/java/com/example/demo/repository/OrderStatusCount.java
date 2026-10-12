package com.example.demo.repository;

import com.example.demo.enums.OrderStatus;

/**
 * 按状态聚合的行数：JPQL 构造表达式投影，避免 {@code Object[]} 与下标约定。
 */
public record OrderStatusCount(OrderStatus status, long count) {
}
