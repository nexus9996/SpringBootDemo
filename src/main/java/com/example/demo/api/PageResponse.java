package com.example.demo.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页负载：作为 {@code ApiResponse<T>} 的 {@code T} 使用，与统一响应包体解耦。
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /** 页码对外统一从 1 开始（Spring Data 内部从 0 开始）。 */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
