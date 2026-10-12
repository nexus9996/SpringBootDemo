package com.example.demo.model.query;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.util.Strings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * 分页 + 排序查询契约。
 *
 * <p>约定：
 * <ul>
 *   <li>页码对外从 <b>1</b> 开始，内部转换为 Spring Data 的 0 基页码；</li>
 *   <li>{@code size} 会被夹到 {@code [1, 100]}，避免客户端拉全表；</li>
 *   <li>排序字段必须命中白名单，否则返回 400 —— 既防止拼错的字段静默返回"另一种顺序"，
 *       也阻断通过属性路径构造非法 JPQL 的注入面。</li>
 * </ul>
 */
public interface SortableQuery {

    int DEFAULT_PAGE = 1;
    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 100;

    Integer page();

    Integer size();

    String sortBy();

    String sortDir();

    /** 允许排序的实体属性白名单。 */
    Set<String> sortableFields();

    /** 未指定排序字段时使用的字段，必须包含在 {@link #sortableFields()} 中。 */
    String defaultSortField();

    default int pageNumber() {
        var requested = page() == null ? DEFAULT_PAGE : page();
        return Math.max(requested, DEFAULT_PAGE) - 1;
    }

    default int pageSize() {
        var requested = size() == null ? DEFAULT_SIZE : size();
        return Math.clamp(requested, 1, MAX_SIZE);
    }

    default Sort.Direction direction() {
        var raw = Strings.defaultIfBlank(sortDir(), "desc").toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持的排序方向 '%s', 可选值: asc, desc".formatted(raw));
        };
    }

    default Sort toSort() {
        var field = Strings.hasText(sortBy()) ? sortBy() : defaultSortField();
        if (!sortableFields().contains(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持的排序字段 '%s', 可选值: %s".formatted(field, sortableFields().stream().sorted().toList()));
        }
        return Sort.by(direction(), field);
    }

    default Pageable toPageable() {
        return PageRequest.of(pageNumber(), pageSize(), toSort());
    }
}
