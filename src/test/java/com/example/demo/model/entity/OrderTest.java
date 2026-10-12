package com.example.demo.model.entity;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderTest {

    private static Order newOrder() {
        return Order.place("ORD20261012ABCDEFGHIJ", 42L, "备注");
    }

    @Test
    void newOrderStartsAsCreatedWithZeroTotal() {
        var order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.belongsTo(42L)).isTrue();
        assertThat(order.belongsTo(43L)).isFalse();
    }

    @Test
    void totalIsDerivedFromItems() {
        var order = newOrder();
        order.addItem("《Effective Java》", new BigDecimal("99.90"), 2);
        order.addItem("机械键盘", new BigDecimal("399.00"), 1);

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("598.80"));
        assertThat(order.getItems().getFirst().subtotal()).isEqualByComparingTo(new BigDecimal("199.80"));
    }

    @Test
    void itemsBelongToTheirOrder() {
        var order = newOrder();
        order.addItem("鼠标", new BigDecimal("129.00"), 1);

        assertThat(order.getItems().getFirst().getProductName()).isEqualTo("鼠标");
    }

    @Test
    void quantityBelowOneIsRejected() {
        var order = newOrder();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> order.addItem("鼠标", new BigDecimal("129.00"), 0))
                .withMessageContaining("quantity");
    }

    @Test
    void happyPathTransitionsToCompleted() {
        var order = newOrder();

        order.pay();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        order.ship();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void skippingStatesIsRejected() {
        var order = newOrder();

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(order::ship)
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_ILLEGAL))
                .withMessageContaining("已创建");
    }

    @Test
    void completedOrderCannotBeCancelled() {
        var order = newOrder();
        order.pay();
        order.ship();
        order.complete();

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(order::cancel)
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_ILLEGAL));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void paidOrderCanBeCancelled() {
        var order = newOrder();
        order.pay();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
