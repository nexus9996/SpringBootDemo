package com.example.demo.model.entity;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 订单聚合根（充血模型）。
 *
 * <p>状态流转与金额计算属于领域不变量，放在实体内部，任何入口（HTTP、定时任务、消息消费者）
 * 都无法绕过；{@code userId} 只保存标识而不做对象关联，避免跨聚合级联与懒加载耦合。
 */
@Entity
@Table(name = "t_order", uniqueConstraints = @UniqueConstraint(name = "uk_t_order_order_no", columnNames = "order_no"))
public class Order extends BaseEntity {

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 200)
    private String remark;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA 需要
    }

    public static Order place(String orderNo, Long userId, String remark) {
        var order = new Order();
        order.orderNo = Objects.requireNonNull(orderNo, "orderNo");
        order.userId = Objects.requireNonNull(userId, "userId");
        order.remark = remark;
        return order;
    }

    /** 追加订单行并重算总额（金额只由订单行推导，避免双份真相）。 */
    public void addItem(String productName, BigDecimal unitPrice, int quantity) {
        var item = new OrderItem(this, productName, unitPrice, quantity);
        items.add(item);
        recalculateTotal();
    }

    public void pay() {
        transitTo(OrderStatus.PAID);
    }

    public void ship() {
        transitTo(OrderStatus.SHIPPED);
    }

    public void complete() {
        transitTo(OrderStatus.COMPLETED);
    }

    public void cancel() {
        transitTo(OrderStatus.CANCELLED);
    }

    /** 是否下单人本人。 */
    public boolean belongsTo(Long candidateUserId) {
        return Objects.equals(userId, candidateUserId);
    }

    private void transitTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "订单 %s 当前状态为 %s, 不允许变更为 %s".formatted(orderNo, status.label(), target.label()));
        }
        this.status = target;
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getRemark() {
        return remark;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "Order{id=%s, orderNo='%s', status=%s, totalAmount=%s}".formatted(getId(), orderNo, status, totalAmount);
    }
}
