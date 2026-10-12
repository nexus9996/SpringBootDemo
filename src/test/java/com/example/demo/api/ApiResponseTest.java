package com.example.demo.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okCarriesDataAndSuccessCode() {
        var response = ApiResponse.ok("payload");

        assertThat(response).isInstanceOf(ApiResponse.Success.class);
        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.code());
        assertThat(response.message()).isEqualTo(ErrorCode.SUCCESS.message());
        assertThat(response.timestamp()).isPositive();

        var success = (ApiResponse.Success<String>) response;
        assertThat(success.data()).isEqualTo("payload");
    }

    @Test
    void okWithoutDataIsStillSuccess() {
        var response = ApiResponse.ok();

        assertThat(response.code()).isEqualTo(200);
        assertThat(((ApiResponse.Success<?>) response).data()).isNull();
    }

    @Test
    void createdUsesCreatedCode() {
        var response = ApiResponse.created(1L);

        assertThat(response.code()).isEqualTo(ErrorCode.CREATED.code());
        assertThat(((ApiResponse.Success<Long>) response).data()).isEqualTo(1L);
    }

    @Test
    void failUsesErrorCodeMetadata() {
        var response = ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(response).isInstanceOf(ApiResponse.Failure.class);
        assertThat(response.code()).isEqualTo(40400);
        assertThat(response.message()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.message());
        assertThat(response.timestamp()).isPositive();
    }

    @Test
    void failAcceptsCustomMessage() {
        var response = ApiResponse.fail(ErrorCode.VALIDATION_FAILED, "username: 不能为空");

        assertThat(response.code()).isEqualTo(40000);
        assertThat(response.message()).isEqualTo("username: 不能为空");
    }

    @Test
    void pageResponseIsAValidPayload() {
        var page = new PageResponse<>(List.of("a", "b"), 1, 2, 2, 1, false);

        var response = ApiResponse.ok(page);

        assertThat(response.code()).isEqualTo(200);
        assertThat(((ApiResponse.Success<PageResponse<String>>) response).data().content())
                .containsExactly("a", "b");
    }
}
