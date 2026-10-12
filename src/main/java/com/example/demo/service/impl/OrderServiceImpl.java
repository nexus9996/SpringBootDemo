package com.example.demo.service.impl;

import com.example.demo.api.PageResponse;
import com.example.demo.common.exception.NotFoundException;
import com.example.demo.common.generator.OrderNumberGenerator;
import com.example.demo.converter.OrderConverter;
import com.example.demo.enums.OrderStatus;
import com.example.demo.model.dto.CreateOrderRequest;
import com.example.demo.model.dto.OrderDTO;
import com.example.demo.model.dto.OrderStatsDTO;
import com.example.demo.model.entity.Order;
import com.example.demo.model.query.OrderPageQuery;
import com.example.demo.repository.OrderRepository;
import com.example.demo.security.AuthenticatedUser;
import com.example.demo.service.OrderService;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Consumer;

/**
 * 订单用例实现。状态流转规则全部封装在 {@link Order} 聚合内，这里只负责编排与鉴权。
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderConverter converter;

    public OrderServiceImpl(OrderRepository orderRepository, UserRepository userRepository,
                            OrderNumberGenerator orderNumberGenerator, OrderConverter converter) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.converter = converter;
    }

    @Override
    public OrderDTO create(CreateOrderRequest request, AuthenticatedUser requester) {
        if (!userRepository.existsById(requester.id())) {
            throw new NotFoundException("用户", requester.id());
        }
        var order = converter.toEntity(request, orderNumberGenerator.next(), requester.id());
        return converter.toDTO(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getById(Long id, AuthenticatedUser requester) {
        return converter.toDTO(requireAccessibleOrder(id, requester));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderDTO> page(OrderPageQuery query, AuthenticatedUser requester) {
        // 非管理员只能看到自己的订单：用身份覆盖客户端传入的 userId，避免越权查询
        var scoped = requester.isAdmin() ? query : query.withUserId(requester.id());
        var page = orderRepository.findAll(scoped.toSpecification(), scoped.toPageable());
        return PageResponse.of(page.map(converter::toDTO));
    }

    @Override
    public OrderDTO pay(Long id, AuthenticatedUser requester) {
        return transit(id, requester, Order::pay);
    }

    @Override
    public OrderDTO ship(Long id, AuthenticatedUser requester) {
        return transit(id, requester, Order::ship);
    }

    @Override
    public OrderDTO complete(Long id, AuthenticatedUser requester) {
        return transit(id, requester, Order::complete);
    }

    @Override
    public OrderDTO cancel(Long id, AuthenticatedUser requester) {
        return transit(id, requester, Order::cancel);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderDTO> myOrders(OrderPageQuery query, AuthenticatedUser requester) {
        // 显式覆盖 userId：ADMIN 调用 /me 时同样只看自己的订单，避免"我的订单"语义随角色漂移
        return page(query.withUserId(requester.id()), requester);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsDTO stats() {
        var counts = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        Arrays.stream(OrderStatus.values()).forEach(status -> counts.put(status, 0L));
        orderRepository.countGroupedByStatus().forEach(row -> counts.put(row.status(), row.count()));
        var total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OrderStatsDTO(total, counts, orderRepository.sumTotalAmountExcluding(OrderStatus.CANCELLED));
    }

    private OrderDTO transit(Long id, AuthenticatedUser requester, Consumer<Order> action) {
        var order = requireAccessibleOrder(id, requester);
        action.accept(order);
        // 先 flush：@LastModifiedDate 在 flush 时生效，否则响应会回显上次的 updateTime
        orderRepository.saveAndFlush(order);
        return converter.toDTO(order);
    }

    private Order requireAccessibleOrder(Long id, AuthenticatedUser requester) {
        var order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("订单", id));
        if (!requester.isAdmin() && !order.belongsTo(requester.id())) {
            // 隐藏资源存在性：不通过 403/404 的差异暴露他人订单
            throw new NotFoundException("订单", id);
        }
        return order;
    }
}
