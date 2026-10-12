package com.example.demo.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建订单请求。下单人取自 JWT 主体，不接受客户端传入 {@code userId}（防越权）。
 */
public record CreateOrderRequest(
        @Size(max = 200, message = "备注长度不能超过 200")
        String remark,

        @NotEmpty(message = "订单至少需要一个商品")
        @Size(max = 50, message = "单个订单最多 50 个商品")
        List<@Valid CreateOrderItemRequest> items
) {
}
