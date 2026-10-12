package com.example.demo.model.dto;

import com.example.demo.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单统计：{@code countByStatus} 含所有状态（无数据为 0），{@code activeAmount} 为未取消订单金额合计。
 */
public record OrderStatsDTO(
        long totalOrders,
        Map<OrderStatus, Long> countByStatus,
        BigDecimal activeAmount
) {
}
