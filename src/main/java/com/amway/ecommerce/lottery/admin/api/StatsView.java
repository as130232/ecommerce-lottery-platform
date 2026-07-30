package com.amway.ecommerce.lottery.admin.api;

import java.util.List;

public record StatsView(Long activityId, String code, long totalDraws, long thanksCount,
                        List<PrizeStat> prizes) {

    public record PrizeStat(Long prizeId, String name, int totalStock, int remainingStock, int awarded) {
    }
}
