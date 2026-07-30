package com.amway.ecommerce.lottery.admin.api;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AdminRequests {

    private AdminRequests() {
    }

    public record CreateActivityRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Min(1) int perUserDrawLimit,
            Long totalDrawLimit) {
    }

    public record UpdateActivityRequest(
            @NotBlank String name,
            @NotNull ActivityStatus status,
            @Min(1) int perUserDrawLimit,
            Long totalDrawLimit) {
    }

    public record CreatePrizeRequest(
            @NotBlank String name,
            @NotBlank String type,
            @Min(0) @Max(10_000) int probability,
            @Min(0) int totalStock) {
    }

    public record UpdatePrizeRequest(
            @NotBlank String name,
            @Min(0) @Max(10_000) int probability,
            @Min(0) int totalStock,
            @Min(0) int remainingStock) {
    }
}
