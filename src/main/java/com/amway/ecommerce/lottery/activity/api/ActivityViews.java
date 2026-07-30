package com.amway.ecommerce.lottery.activity.api;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import java.util.List;

/** Read models returned by the public activity endpoints. */
public final class ActivityViews {

    private ActivityViews() {
    }

    public record ActivitySummary(Long id, String code, String name, ActivityStatus status,
                                  int perUserDrawLimit) {
        public static ActivitySummary from(LotteryActivity a) {
            return new ActivitySummary(a.getId(), a.getCode(), a.getName(), a.getStatus(),
                    a.getPerUserDrawLimit());
        }
    }

    public record PrizeView(Long id, String name, String type, int probability, int totalStock,
                            int remainingStock) {
        public static PrizeView from(Prize p) {
            return new PrizeView(p.getId(), p.getName(), p.getType().name(), p.getProbability(),
                    p.getTotalStock(), p.getRemainingStock());
        }
    }

    public record ActivityDetail(Long id, String code, String name, ActivityStatus status,
                                 int perUserDrawLimit, Long totalDrawLimit, List<PrizeView> prizes) {
        public static ActivityDetail from(LotteryActivity a, List<Prize> prizes) {
            return new ActivityDetail(a.getId(), a.getCode(), a.getName(), a.getStatus(),
                    a.getPerUserDrawLimit(), a.getTotalDrawLimit(),
                    prizes.stream().map(PrizeView::from).toList());
        }
    }
}
