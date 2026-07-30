package com.amway.ecommerce.lottery.draw.api;

import com.amway.ecommerce.lottery.auth.AuthPrincipal;
import com.amway.ecommerce.lottery.common.api.ApiResponse;
import com.amway.ecommerce.lottery.draw.api.DrawViews.DrawRecordView;
import com.amway.ecommerce.lottery.draw.api.DrawViews.DrawRequest;
import com.amway.ecommerce.lottery.draw.application.DrawBatchResult;
import com.amway.ecommerce.lottery.draw.application.DrawQueryService;
import com.amway.ecommerce.lottery.draw.application.DrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/activities/{activityId}")
@Tag(name = "Draw", description = "抽獎 (需登入)")
@SecurityRequirement(name = "bearer-jwt")
public class DrawController {

    private final DrawService drawService;
    private final DrawQueryService drawQueryService;

    public DrawController(DrawService drawService, DrawQueryService drawQueryService) {
        this.drawService = drawService;
        this.drawQueryService = drawQueryService;
    }

    @PostMapping("/draws")
    @Operation(summary = "抽獎 (單抽 / 連抽)")
    public ApiResponse<DrawBatchResult> draw(@PathVariable Long activityId,
                                             @Valid @RequestBody DrawRequest request,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();
        DrawBatchResult result = drawService.draw(activityId, principal.userId(), request.times(), idempotencyKey);
        return ApiResponse.ok(result);
    }

    @GetMapping("/my-records")
    @Operation(summary = "查詢自己的抽獎紀錄")
    public ApiResponse<List<DrawRecordView>> myRecords(@PathVariable Long activityId,
                                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(drawQueryService.myRecords(activityId, principal.userId()));
    }
}
