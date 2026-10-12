package com.example.demo.controller;

import com.example.demo.api.ApiResponse;
import com.example.demo.api.PageResponse;
import com.example.demo.model.dto.CreateOrderRequest;
import com.example.demo.model.dto.OrderDTO;
import com.example.demo.model.dto.OrderStatsDTO;
import com.example.demo.model.query.OrderPageQuery;
import com.example.demo.security.CurrentUserProvider;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：下单与支付由本人发起，发货/完成/全局查询/统计属于运营动作（ADMIN）。
 *
 * <p>访问他人订单统一返回 404（不区分"不存在"与"无权访问"），避免通过状态码枚举资源。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;

    public OrderController(OrderService orderService, CurrentUserProvider currentUserProvider) {
        this.orderService = orderService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDTO> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created(orderService.create(request, currentUserProvider.require()));
    }

    /** 管理员视角：可跨用户、按状态/订单号筛选。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<OrderDTO>> page(OrderPageQuery query) {
        return ApiResponse.ok(orderService.page(query, currentUserProvider.require()));
    }

    /** 我的订单：服务端强制限定为本人订单（ADMIN 调用同样只看到自己的）。 */
    @GetMapping("/me")
    public ApiResponse<PageResponse<OrderDTO>> myOrders(OrderPageQuery query) {
        return ApiResponse.ok(orderService.myOrders(query, currentUserProvider.require()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OrderStatsDTO> stats() {
        return ApiResponse.ok(orderService.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDTO> getById(@PathVariable @Positive Long id) {
        return ApiResponse.ok(orderService.getById(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderDTO> pay(@PathVariable @Positive Long id) {
        return ApiResponse.ok(orderService.pay(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OrderDTO> ship(@PathVariable @Positive Long id) {
        return ApiResponse.ok(orderService.ship(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OrderDTO> complete(@PathVariable @Positive Long id) {
        return ApiResponse.ok(orderService.complete(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDTO> cancel(@PathVariable @Positive Long id) {
        return ApiResponse.ok(orderService.cancel(id, currentUserProvider.require()));
    }
}
