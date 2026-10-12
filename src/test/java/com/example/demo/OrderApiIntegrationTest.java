package com.example.demo;

import com.example.demo.model.dto.CreateOrderItemRequest;
import com.example.demo.model.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单接口端到端：下单金额计算、状态机流转、角色与归属鉴权、统计聚合。
 */
class OrderApiIntegrationTest extends AbstractApiTest {

    private static final String ORDER_NO_PATTERN = "^ORD\\d{8}[A-HJ-NP-Z2-9]{10}$";

    @Test
    void userCreatesOrderAndSystemComputesTotal() throws Exception {
        var result = createOrder(userToken(), "请在工作日配送");

        assertThat(code(result)).isEqualTo(201);
        var data = data(result);
        assertThat(data.path("orderNo").asString()).matches(ORDER_NO_PATTERN);
        assertThat(data.path("status").asString()).isEqualTo("CREATED");
        assertThat(data.path("statusLabel").asString()).isEqualTo("已创建");
        assertThat(data.path("totalAmount").asDouble()).isEqualTo(906.00);
        assertThat(data.path("remark").asString()).isEqualTo("请在工作日配送");
        assertThat(data.path("items").size()).isEqualTo(2);
        assertThat(data.path("items").get(0).path("subtotal").asDouble()).isEqualTo(108.00);
        assertThat(data.path("items").get(1).path("subtotal").asDouble()).isEqualTo(798.00);
        assertThat(data.path("allowedNextStatuses").toString()).isEqualTo("[\"PAID\",\"CANCELLED\"]");
    }

    @Test
    void orderCreationRejectsEmptyItems() throws Exception {
        var result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(null, List.of()))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(body(result).path("message").asString()).contains("订单至少需要一个商品");
    }

    @Test
    void orderCreationRejectsNonPositiveQuantityAndPrice() throws Exception {
        var result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(null, List.of(
                                new CreateOrderItemRequest("显示器", new BigDecimal("0.00"), 0))))))
                .andExpect(status().isBadRequest())
                .andReturn();

        var message = body(result).path("message").asString();
        assertThat(message).contains("unitPrice").contains("quantity");
    }

    @Test
    void ownerPaysAndAdminDrivesRemainingTransitions() throws Exception {
        var orderId = orderId(createOrder(userToken(), null));

        var paid = mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(paid).path("status").asString()).isEqualTo("PAID");

        var shipped = mockMvc.perform(post("/api/orders/" + orderId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(shipped).path("status").asString()).isEqualTo("SHIPPED");

        var completed = mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(completed).path("status").asString()).isEqualTo("COMPLETED");
        assertThat(data(completed).path("allowedNextStatuses").toString()).isEqualTo("[]");
    }

    @Test
    void userCannotShipOrCompleteOrders() throws Exception {
        var orderId = orderId(createOrder(userToken(), null));
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk());

        var ship = mockMvc.perform(post("/api/orders/" + orderId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(code(ship)).isEqualTo(40300);

        mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        var orderId = orderId(createOrder(userToken(), null));

        var result = mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(code(result)).isEqualTo(40902);
        assertThat(body(result).path("message").asString()).contains("已创建");
    }

    @Test
    void ownerCanCancelCreatedOrder() throws Exception {
        var orderId = orderId(createOrder(userToken(), null));

        var result = mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).path("status").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void strangerCannotReadOthersOrder() throws Exception {
        var orderId = orderId(createOrder(userToken(), null));
        var stranger = uniqueUsername("stranger");
        registerUser(stranger);

        var result = mockMvc.perform(get("/api/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(stranger, REGISTERED_PASSWORD))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(code(result)).isEqualTo(40400);
    }

    @Test
    void myOrdersReturnsOnlyOwnOrders() throws Exception {
        var stranger = uniqueUsername("nosy");
        registerUser(stranger);
        var token = token(stranger, REGISTERED_PASSWORD);

        var result = mockMvc.perform(get("/api/orders/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).path("content").size()).isZero();
        assertThat(data(result).path("totalElements").asLong()).isZero();
    }

    @Test
    void adminMyOrdersStaysScopedToTheAdmin() throws Exception {
        createOrder(userToken(), null);

        var mine = mockMvc.perform(get("/api/orders/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(mine).path("totalElements").asLong())
                .as("ADMIN 调用 /me 也应只看自己的订单")
                .isZero();

        var all = mockMvc.perform(get("/api/orders").param("size", "5").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(all).path("totalElements").asLong()).isGreaterThan(0);
    }

    @Test
    void mutationResponseCarriesFreshUpdateTime() throws Exception {
        var created = createOrder(userToken(), null);
        var orderId = orderId(created);
        var createdUpdateTime = data(created).path("updateTime").asString();

        var paid = mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();
        var paidUpdateTime = data(paid).path("updateTime").asString();

        var fetched = mockMvc.perform(get("/api/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(paidUpdateTime).isGreaterThanOrEqualTo(createdUpdateTime);
        assertThat(data(fetched).path("updateTime").asString())
                .as("变更响应里的 updateTime 必须与后续读取一致（不能是 flush 前的旧值）")
                .isEqualTo(paidUpdateTime);
    }

    @Test
    void unitPriceBeyondColumnPrecisionIsRejected() throws Exception {
        var tooLarge = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(null, List.of(
                                new CreateOrderItemRequest("天价商品", new BigDecimal("99999999999999.99"), 2))))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(body(tooLarge).path("message").asString()).contains("13 位整数");

        var boundary = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(null, List.of(
                                new CreateOrderItemRequest("边界商品", new BigDecimal("9999999999999.99"), 1))))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(data(boundary).path("totalAmount").asDouble()).isEqualTo(9999999999999.99);
    }

    @Test
    void userCannotListAllOrdersButAdminCan() throws Exception {
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden());

        var result = mockMvc.perform(get("/api/orders")
                        .param("size", "5").param("sortBy", "id").param("sortDir", "desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).path("content").size()).isGreaterThan(0);
    }

    @Test
    void statsReflectNewlyPaidOrder() throws Exception {
        var before = stats();
        var created = createOrder(userToken(), null);
        var total = data(created).path("totalAmount").asDouble();
        var orderId = orderId(created);
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk());

        var after = stats();

        assertThat(after.path("countByStatus").has("CREATED")).isTrue();
        assertThat(after.path("countByStatus").has("PAID")).isTrue();
        assertThat(after.path("countByStatus").has("SHIPPED")).isTrue();
        assertThat(after.path("countByStatus").has("COMPLETED")).isTrue();
        assertThat(after.path("countByStatus").has("CANCELLED")).isTrue();
        assertThat(after.path("countByStatus").path("PAID").asLong())
                .isEqualTo(before.path("countByStatus").path("PAID").asLong() + 1);
        assertThat(after.path("totalOrders").asLong())
                .isEqualTo(before.path("totalOrders").asLong() + 1);
        assertThat(after.path("activeAmount").asDouble())
                .isEqualTo(before.path("activeAmount").asDouble() + total);
    }

    @Test
    void cancelledOrdersAreExcludedFromActiveAmount() throws Exception {
        var before = stats();
        var created = createOrder(userToken(), null);
        var total = data(created).path("totalAmount").asDouble();
        var orderId = orderId(created);
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isOk());

        var after = stats();

        assertThat(after.path("activeAmount").asDouble()).isEqualTo(before.path("activeAmount").asDouble());
        assertThat(after.path("countByStatus").path("CANCELLED").asLong())
                .isEqualTo(before.path("countByStatus").path("CANCELLED").asLong() + 1);
        assertThat(after.path("countByStatus").path("CREATED").asLong())
                .isEqualTo(before.path("countByStatus").path("CREATED").asLong());
        assertThat(total).isPositive();
    }

    private org.springframework.test.web.servlet.MvcResult createOrder(String token, String remark) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(remark, List.of(
                                new CreateOrderItemRequest("《领域驱动设计》", new BigDecimal("108.00"), 1),
                                new CreateOrderItemRequest("机械键盘", new BigDecimal("399.00"), 2))))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long orderId(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    private tools.jackson.databind.JsonNode stats() throws Exception {
        var result = mockMvc.perform(get("/api/orders/stats").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn();
        return data(result);
    }
}
