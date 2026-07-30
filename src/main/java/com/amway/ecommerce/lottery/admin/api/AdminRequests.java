package com.amway.ecommerce.lottery.admin.api;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AdminRequests {

    private AdminRequests() {
    }

    public record CreateActivityRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 100) String name,
            @Min(1) int perUserDrawLimit,
            @Positive Long totalDrawLimit) {
    }

    public record UpdateActivityRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull ActivityStatus status,
            @Min(1) int perUserDrawLimit,
            @Positive Long totalDrawLimit) {
    }

    public record CreatePrizeRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank String type,
            @Min(1) @Max(10_000) int probability,
            @Min(0) int totalStock) {
    }

    public record UpdatePrizeRequest(
            @NotBlank @Size(max = 100) String name,
            @Min(0) @Max(10_000) int probability,
            @Min(0) int totalStock,
            @Min(0) int remainingStock) {
    }
}
