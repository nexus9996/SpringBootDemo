package com.example.demo.model.query;

import com.example.demo.api.ErrorCode;
import com.example.demo.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortableQueryTest {

    record Probe(Integer page, Integer size, String sortBy, String sortDir) implements SortableQuery {

        @Override
        public Set<String> sortableFields() {
            return Set.of("id", "createTime");
        }

        @Override
        public String defaultSortField() {
            return "createTime";
        }
    }

    @Test
    void defaultsToOneBasedFirstPageWithDefaultSize() {
        var probe = new Probe(null, null, null, null);

        assertThat(probe.pageNumber()).isZero();
        assertThat(probe.pageSize()).isEqualTo(SortableQuery.DEFAULT_SIZE);
        assertThat(probe.toSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createTime"));
    }

    @Test
    void convertsOneBasedPageToPageRequest() {
        var pageable = new Probe(3, 50, "id", "asc").toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void clampsOutOfRangePaging() {
        assertThat(new Probe(0, 0, null, null).pageNumber()).isZero();
        assertThat(new Probe(0, 0, null, null).pageSize()).isEqualTo(1);
        assertThat(new Probe(-5, 10_000, null, null).pageSize()).isEqualTo(SortableQuery.MAX_SIZE);
        assertThat(new Probe(-5, 10_000, null, null).pageNumber()).isZero();
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> new Probe(1, 20, "password", "asc").toSort())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("password");
    }

    @Test
    void rejectsUnknownSortDirection() {
        assertThatThrownBy(() -> new Probe(1, 20, "id", "sideways").toSort())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asc, desc");
    }

    @Test
    void sortDirectionIsCaseInsensitive() {
        assertThat(new Probe(1, 20, "id", "ASC").toSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }
}
