package com.example.demo.enums;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 订单状态机。
 *
 * <p>把"允许流转"的规则收敛在枚举里，业务代码只表达意图（{@code order.pay()}），
 * 非法流转统一抛 {@code BusinessException(ORDER_STATUS_ILLEGAL)}。
 */
public enum OrderStatus {

    CREATED("已创建"),
    PAID("已支付"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            CREATED, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 当前状态允许流转到的目标状态（供前端渲染可用操作）。 */
    public Set<OrderStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    public boolean canTransitionTo(OrderStatus target) {
        Objects.requireNonNull(target, "target");
        return TRANSITIONS.get(this).contains(target);
    }
}
