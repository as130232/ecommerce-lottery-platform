package com.amway.ecommerce.lottery.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform envelope for every REST response so the frontend can rely on a single
 * shape: {@code success/code/message/data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
