package com.example.demo.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 下单明细行。
 */
public record CreateOrderItemRequest(
        @NotBlank
        @Size(max = 64, message = "商品名称长度不能超过 64")
        String productName,

        @NotNull
        @DecimalMin(value = "0.01", message = "单价必须大于 0")
        // 上限取 13 位整数：50 行 × 999 数量 × 10^13 仍在 total_amount numeric(18,2) 范围内
        @Digits(integer = 13, fraction = 2, message = "单价最多 13 位整数、2 位小数")
        BigDecimal unitPrice,

        @Min(value = 1, message = "数量必须大于 0")
        @Max(value = 999, message = "单行数量不能超过 999")
        int quantity
) {
}
