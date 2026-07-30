package com.amway.ecommerce.lottery.admin.api;

import com.amway.ecommerce.lottery.activity.api.ActivityViews.ActivitySummary;
import com.amway.ecommerce.lottery.activity.api.ActivityViews.PrizeView;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.CreateActivityRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.CreatePrizeRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.UpdateActivityRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.UpdatePrizeRequest;
import com.amway.ecommerce.lottery.admin.application.AdminService;
import com.amway.ecommerce.lottery.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for dynamic prize/activity configuration. Guarded by
 * {@code hasRole('ADMIN')} in SecurityConfig for the whole /api/admin/** tree.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "動態配置 (需 ADMIN 權限)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/activities")
    @Operation(summary = "列出所有活動（含未上架 DRAFT）")
    public ApiResponse<List<ActivitySummary>> listActivities() {
        return ApiResponse.ok(adminService.listActivities().stream().map(ActivitySummary::from).toList());
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "建立抽獎活動")
    public ApiResponse<ActivitySummary> createActivity(@Valid @RequestBody CreateActivityRequest req) {
        return ApiResponse.ok(ActivitySummary.from(adminService.createActivity(req)));
    }

    @PutMapping("/activities/{id}")
    @Operation(summary = "更新活動 (狀態設為 ACTIVE 時會驗證機率總和 = 100%)")
    public ApiResponse<ActivitySummary> updateActivity(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateActivityRequest req) {
        return ApiResponse.ok(ActivitySummary.from(adminService.updateActivity(id, req)));
    }

    @PostMapping("/activities/{id}/prizes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增獎品")
    public ApiResponse<PrizeView> addPrize(@PathVariable Long id,
                                           @Valid @RequestBody CreatePrizeRequest req) {
        return ApiResponse.ok(PrizeView.from(adminService.addPrize(id, req)));
    }

    @PutMapping("/prizes/{prizeId}")
    @Operation(summary = "修改獎品名稱 / 機率 / 庫存 (即時生效並刷新快取)")
    public ApiResponse<PrizeView> updatePrize(@PathVariable Long prizeId,
                                              @Valid @RequestBody UpdatePrizeRequest req) {
        return ApiResponse.ok(PrizeView.from(adminService.updatePrize(prizeId, req)));
    }

    @DeleteMapping("/prizes/{prizeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "刪除獎品（無抽獎紀錄才可刪）")
    public void deletePrize(@PathVariable Long prizeId) {
        adminService.deletePrize(prizeId);
    }

    @GetMapping("/activities/{id}/stats")
    @Operation(summary = "活動抽獎統計")
    public ApiResponse<StatsView> stats(@PathVariable Long id) {
        return ApiResponse.ok(adminService.stats(id));
    }
}
