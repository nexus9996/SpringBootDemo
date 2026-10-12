package com.example.demo.model.dto;

import com.example.demo.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单响应：{@code allowedNextStatuses} 告诉客户端当前可执行的状态流转，避免前端硬编码状态机。
 */
public record OrderDTO(
        Long id,
        String orderNo,
        Long userId,
        OrderStatus status,
        String statusLabel,
        BigDecimal totalAmount,
        String remark,
        List<OrderItemDTO> items,
        List<OrderStatus> allowedNextStatuses,
        Instant createTime,
        Instant updateTime
) {
}
