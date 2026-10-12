package com.example.demo.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void convertsSpringPageToOneBasedPaging() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        var response = PageResponse.of(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void lastPageReportsNoNextPage() {
        var page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);

        var response = PageResponse.of(page);

        assertThat(response.page()).isEqualTo(3);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void emptyPageIsValid() {
        var response = PageResponse.of(new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
    }
}
