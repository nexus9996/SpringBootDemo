package com.example.demo.converter;

import com.example.demo.model.dto.CreateOrderRequest;
import com.example.demo.model.dto.OrderDTO;
import com.example.demo.model.dto.OrderItemDTO;
import com.example.demo.model.entity.Order;
import com.example.demo.model.entity.OrderItem;
import org.springframework.stereotype.Component;

/**
 * 订单实体 ↔ DTO 转换。金额与状态标签直接取自聚合根，避免客户端重复实现领域规则。
 */
@Component
public class OrderConverter {

    public Order toEntity(CreateOrderRequest request, String orderNo, Long userId) {
        var order = Order.place(orderNo, userId, request.remark());
        request.items().forEach(item -> order.addItem(item.productName(), item.unitPrice(), item.quantity()));
        return order;
    }

    public OrderDTO toDTO(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getStatus().label(),
                order.getTotalAmount(),
                order.getRemark(),
                order.getItems().stream().map(this::toDTO).toList(),
                order.getStatus().allowedTransitions().stream().sorted().toList(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    public OrderItemDTO toDTO(OrderItem item) {
        return new OrderItemDTO(
                item.getId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.subtotal()
        );
    }
}
