package com.amway.ecommerce.lottery.activity.api;

import com.amway.ecommerce.lottery.activity.api.ActivityViews.ActivityDetail;
import com.amway.ecommerce.lottery.activity.api.ActivityViews.ActivitySummary;
import com.amway.ecommerce.lottery.activity.application.ActivityQueryService;
import com.amway.ecommerce.lottery.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "Activities", description = "抽獎活動查詢")
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    public ActivityController(ActivityQueryService activityQueryService) {
        this.activityQueryService = activityQueryService;
    }

    @GetMapping
    @Operation(summary = "列出進行中的活動")
    public ApiResponse<List<ActivitySummary>> listActive() {
        return ApiResponse.ok(activityQueryService.listActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查詢活動明細與獎品設定")
    public ApiResponse<ActivityDetail> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(activityQueryService.getDetail(id));
    }
}
