package com.example.demo.service;

import com.example.demo.api.PageResponse;
import com.example.demo.model.dto.CreateOrderRequest;
import com.example.demo.model.dto.OrderDTO;
import com.example.demo.model.dto.OrderStatsDTO;
import com.example.demo.model.query.OrderPageQuery;
import com.example.demo.security.AuthenticatedUser;

/**
 * 订单应用服务。
 *
 * <p>数据级鉴权在服务内完成（{@link AuthenticatedUser} 显式传入），角色级鉴权交给
 * {@code @PreAuthorize}；跨用户访问返回 404 以避免通过状态码枚举他人订单。
 */
public interface OrderService {

    OrderDTO create(CreateOrderRequest request, AuthenticatedUser requester);

    OrderDTO getById(Long id, AuthenticatedUser requester);

    PageResponse<OrderDTO> page(OrderPageQuery query, AuthenticatedUser requester);

    /** 我的订单：无论什么角色，都强制限定为调用者本人，忽略请求中的 userId。 */
    PageResponse<OrderDTO> myOrders(OrderPageQuery query, AuthenticatedUser requester);

    OrderDTO pay(Long id, AuthenticatedUser requester);

    OrderDTO ship(Long id, AuthenticatedUser requester);

    OrderDTO complete(Long id, AuthenticatedUser requester);

    OrderDTO cancel(Long id, AuthenticatedUser requester);

    OrderStatsDTO stats();
}
