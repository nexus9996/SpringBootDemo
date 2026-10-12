package com.example.demo.model.query;

import com.example.demo.common.util.Strings;
import com.example.demo.enums.UserStatus;
import com.example.demo.model.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * 用户分页查询条件（GET 查询串绑定：{@code keyword/status/page/size/sortBy/sortDir}）。
 */
public record UserPageQuery(
        String keyword,
        UserStatus status,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
) implements SortableQuery {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "username", "email", "status", "createTime", "updateTime");

    @Override
    public Set<String> sortableFields() {
        return SORTABLE_FIELDS;
    }

    @Override
    public String defaultSortField() {
        return "id";
    }

    /** 关键字模糊匹配用户名/邮箱，其余条件为精确匹配。 */
    public Specification<User> toSpecification() {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (Strings.hasText(keyword)) {
                var pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("username")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
