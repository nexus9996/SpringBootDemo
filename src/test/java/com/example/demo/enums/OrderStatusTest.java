package com.example.demo.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
        "CREATED, PAID",
        "CREATED, CANCELLED",
        "PAID, SHIPPED",
        "PAID, CANCELLED",
        "SHIPPED, COMPLETED"
    })
    void allowsForwardTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, SHIPPED",
        "CREATED, COMPLETED",
        "PAID, COMPLETED",
        "SHIPPED, PAID",
        "SHIPPED, CANCELLED",
        "COMPLETED, CANCELLED",
        "CANCELLED, PAID",
        "PAID, CREATED",
        "CREATED, CREATED"
    })
    void rejectsBackwardAndSkippingTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void terminalStatusesAcceptNothing() {
        assertThat(OrderStatus.COMPLETED.allowedTransitions()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedTransitions()).isEmpty();
        assertThat(OrderStatus.CREATED.allowedTransitions()).isNotEmpty();
    }

    @Test
    void everyStatusHasALabel() {
        assertThat(OrderStatus.values()).allSatisfy(status -> assertThat(status.label()).isNotBlank());
    }

    @Test
    void nullTargetIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> OrderStatus.CREATED.canTransitionTo(null));
    }
}
