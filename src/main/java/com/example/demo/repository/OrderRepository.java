package com.example.demo.repository;

import com.example.demo.enums.OrderStatus;
import com.example.demo.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单仓储。
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** 一次 group by 拿到全部状态的计数（统计接口用它，而不是按状态发 N 条查询）。 */
    @Query("select new com.example.demo.repository.OrderStatusCount(o.status, count(o)) from Order o group by o.status")
    List<OrderStatusCount> countGroupedByStatus();

    /** 未取消订单的金额合计；无数据时返回 0。 */
    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status <> :excluded")
    BigDecimal sumTotalAmountExcluding(@Param("excluded") OrderStatus excluded);
}
