package com.example.demo.model.dto;

import java.math.BigDecimal;

/**
 * 订单行响应。
 */
public record OrderItemDTO(
        Long id,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
) {
}
