package com.example.demo.converter;

import com.example.demo.enums.OrderStatus;
import com.example.demo.model.dto.CreateOrderItemRequest;
import com.example.demo.model.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConverterTest {

    private final OrderConverter converter = new OrderConverter();

    @Test
    void buildsOrderWithItemsAndTotal() {
        var request = new CreateOrderRequest("请在工作日配送", List.of(
                new CreateOrderItemRequest("《领域驱动设计》", new BigDecimal("108.00"), 1),
                new CreateOrderItemRequest("机械键盘", new BigDecimal("399.00"), 2)));

        var order = converter.toEntity(request, "ORD20261012ABCDEFGHIJ", 7L);

        assertThat(order.getOrderNo()).isEqualTo("ORD20261012ABCDEFGHIJ");
        assertThat(order.getUserId()).isEqualTo(7L);
        assertThat(order.getRemark()).isEqualTo("请在工作日配送");
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("906.00"));
    }

    @Test
    void dtoExposesStatusLabelSubtotalsAndAllowedTransitions() {
        var order = converter.toEntity(new CreateOrderRequest(null, List.of(
                new CreateOrderItemRequest("显示器", new BigDecimal("1299.00"), 1))), "ORD-X", 1L);

        var dto = converter.toDTO(order);

        assertThat(dto.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(dto.statusLabel()).isEqualTo("已创建");
        assertThat(dto.allowedNextStatuses()).containsExactly(OrderStatus.PAID, OrderStatus.CANCELLED);
        assertThat(dto.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("显示器");
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("1299.00"));
        });
        assertThat(dto.totalAmount()).isEqualByComparingTo(new BigDecimal("1299.00"));
    }

    @Test
    void terminalOrderHasNoAllowedTransitions() {
        var order = converter.toEntity(new CreateOrderRequest(null, List.of(
                new CreateOrderItemRequest("显示器", new BigDecimal("1299.00"), 1))), "ORD-Y", 1L);
        order.pay();
        order.ship();
        order.complete();

        var dto = converter.toDTO(order);

        assertThat(dto.allowedNextStatuses()).isEmpty();
        assertThat(dto.statusLabel()).isEqualTo("已完成");
    }
}
