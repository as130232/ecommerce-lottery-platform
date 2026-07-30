package com.amway.ecommerce.lottery.activity.application;

import com.amway.ecommerce.lottery.activity.api.ActivityViews.ActivityDetail;
import com.amway.ecommerce.lottery.activity.api.ActivityViews.ActivitySummary;
import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.common.exception.ResourceNotFoundException;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityQueryService {

    private final LotteryActivityRepository activityRepository;
    private final PrizeRepository prizeRepository;

    public ActivityQueryService(LotteryActivityRepository activityRepository, PrizeRepository prizeRepository) {
        this.activityRepository = activityRepository;
        this.prizeRepository = prizeRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivitySummary> listActive() {
        return activityRepository.findByStatus(ActivityStatus.ACTIVE).stream()
                .map(ActivitySummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivityDetail getDetail(Long activityId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("活動不存在: " + activityId));
        return ActivityDetail.from(activity, prizeRepository.findByActivityId(activityId));
    }
}
