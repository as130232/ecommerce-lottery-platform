package com.amway.ecommerce.lottery.draw.api;

import com.amway.ecommerce.lottery.draw.domain.DrawResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

public final class DrawViews {

    private DrawViews() {
    }

    public record DrawRequest(
            @Schema(description = "抽獎次數 (單抽=1，連抽最多 10)", example = "1")
            @Min(1) @Max(10) int times,
            @Schema(description = "冪等鍵，重送相同鍵不會重複抽獎；留空由後端產生")
            String idempotencyKey) {
    }

    public record DrawRecordView(Long prizeId, String prizeName, DrawResult result, LocalDateTime createdAt) {
    }
}
