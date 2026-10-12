package com.example.demo.service.impl;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.common.generator.OrderNumberGenerator;
import com.example.demo.converter.OrderConverter;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.Role;
import com.example.demo.model.dto.CreateOrderItemRequest;
import com.example.demo.model.dto.CreateOrderRequest;
import com.example.demo.model.entity.Order;
import com.example.demo.model.query.OrderPageQuery;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusCount;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class OrderServiceImplTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(7L, "alice", Set.of(Role.USER));
    private static final AuthenticatedUser OTHER_USER = new AuthenticatedUser(8L, "bob", Set.of(Role.USER));
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", Set.of(Role.ADMIN, Role.USER));

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
    private final OrderConverter converter = new OrderConverter();

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, userRepository, orderNumberGenerator, converter);
    }

    @Test
    void createFailsWhenRequesterDoesNotExist() {
        given(userRepository.existsById(7L)).willReturn(false);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.create(request(), OWNER));

        then(orderRepository).should(never()).save(any());
    }

    @Test
    void createPersistsOrderWithGeneratedNumberAndComputedTotal() {
        given(userRepository.existsById(7L)).willReturn(true);
        given(orderNumberGenerator.next()).willReturn("ORD20261012ABCDEFGHIJ");
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create(request(), OWNER);

        var saved = capturedOrder();
        assertThat(saved.getOrderNo()).isEqualTo("ORD20261012ABCDEFGHIJ");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("906.00"));
        assertThat(dto.items()).hasSize(2);
        assertThat(dto.allowedNextStatuses()).containsExactly(OrderStatus.PAID, OrderStatus.CANCELLED);
    }

    @Test
    void ownerCanReadOwnOrder() {
        var order = orderOf(7L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThat(service.getById(1L, OWNER).orderNo()).isEqualTo("ORD-X");
    }

    @Test
    void adminCanReadAnyOrder() {
        var order = orderOf(7L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThat(service.getById(1L, ADMIN).userId()).isEqualTo(7L);
    }

    @Test
    void strangerGetsNotFoundInsteadOfForbidden() {
        given(orderRepository.findById(1L)).willReturn(Optional.of(orderOf(7L)));

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getById(1L, OTHER_USER))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void ownerPaysOwnOrder() {
        var order = orderOf(7L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThat(service.pay(1L, OWNER).status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void illegalTransitionIsRejected() {
        var order = orderOf(7L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.complete(1L, OWNER))
                .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_ILLEGAL));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void pageUsesPagingAndMapsContent() {
        given(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(orderOf(7L)), PageRequest.of(0, 20), 1));

        var page = service.page(new OrderPageQuery(null, null, null, 1, 20, "id", "desc"), OWNER);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement().satisfies(dto -> assertThat(dto.userId()).isEqualTo(7L));
    }

    @Test
    void statsZeroFillMissingStatusesAndSumActiveAmount() {
        given(orderRepository.countGroupedByStatus())
                .willReturn(List.of(new OrderStatusCount(OrderStatus.PAID, 3), new OrderStatusCount(OrderStatus.CANCELLED, 1)));
        given(orderRepository.sumTotalAmountExcluding(OrderStatus.CANCELLED)).willReturn(new BigDecimal("906.00"));

        var stats = service.stats();

        assertThat(stats.totalOrders()).isEqualTo(4);
        assertThat(stats.activeAmount()).isEqualByComparingTo("906.00");
        assertThat(stats.countByStatus()).containsOnlyKeys(OrderStatus.values());
        assertThat(stats.countByStatus().get(OrderStatus.PAID)).isEqualTo(3);
        assertThat(stats.countByStatus().get(OrderStatus.CREATED)).isZero();
        assertThat(stats.countByStatus().get(OrderStatus.COMPLETED)).isZero();
    }

    private static CreateOrderRequest request() {
        return new CreateOrderRequest("请在工作日配送", List.of(
                new CreateOrderItemRequest("《领域驱动设计》", new BigDecimal("108.00"), 1),
                new CreateOrderItemRequest("机械键盘", new BigDecimal("399.00"), 2)));
    }

    private Order capturedOrder() {
        var captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private static Order orderOf(Long userId) {
        var order = Order.place("ORD-X", userId, null);
        order.addItem("显示器", new BigDecimal("1299.00"), 1);
        return order;
    }
}
