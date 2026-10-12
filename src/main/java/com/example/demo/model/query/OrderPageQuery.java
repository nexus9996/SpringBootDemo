package com.example.demo.model.query;

import com.example.demo.common.util.Strings;
import com.example.demo.enums.OrderStatus;
import com.example.demo.model.entity.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * 订单分页查询条件。{@code userId} 由服务端根据身份强制注入，不接受客户端指定（见 {@code OrderController}）。
 */
public record OrderPageQuery(
        String orderNo,
        OrderStatus status,
        Long userId,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
) implements SortableQuery {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "orderNo", "status", "totalAmount", "createTime", "updateTime");

    @Override
    public Set<String> sortableFields() {
        return SORTABLE_FIELDS;
    }

    @Override
    public String defaultSortField() {
        return "id";
    }

    public OrderPageQuery withUserId(Long forcedUserId) {
        return new OrderPageQuery(orderNo, status, forcedUserId, page, size, sortBy, sortDir);
    }

    public Specification<Order> toSpecification() {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (Strings.hasText(orderNo)) {
                var pattern = "%" + orderNo.trim().toUpperCase(Locale.ROOT) + "%";
                predicates.add(builder.like(builder.upper(root.get("orderNo")), pattern));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (userId != null) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
