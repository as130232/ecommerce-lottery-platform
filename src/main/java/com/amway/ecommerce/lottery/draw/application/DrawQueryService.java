package com.amway.ecommerce.lottery.draw.application;

import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.common.exception.ResourceNotFoundException;
import com.amway.ecommerce.lottery.draw.api.DrawViews.DrawRecordView;
import com.amway.ecommerce.lottery.draw.api.DrawViews.QuotaView;
import com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrawQueryService {

    private static final int MAX_RECORDS = 50;

    private final DrawRecordRepository drawRecordRepository;
    private final PrizeRepository prizeRepository;
    private final LotteryActivityRepository activityRepository;

    public DrawQueryService(DrawRecordRepository drawRecordRepository, PrizeRepository prizeRepository,
                            LotteryActivityRepository activityRepository) {
        this.drawRecordRepository = drawRecordRepository;
        this.prizeRepository = prizeRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public QuotaView myQuota(Long activityId, Long userId) {
        LotteryActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("活動不存在: " + activityId));
        long used = drawRecordRepository.countByActivityIdAndUserId(activityId, userId);
        long remaining = Math.max(0, activity.getPerUserDrawLimit() - used);
        return new QuotaView(activity.getPerUserDrawLimit(), used, remaining);
    }

    @Transactional(readOnly = true)
    public List<DrawRecordView> myRecords(Long activityId, Long userId) {
        Map<Long, String> names = prizeRepository.findByActivityId(activityId).stream()
                .collect(Collectors.toMap(Prize::getId, Prize::getName));
        return drawRecordRepository
                .findByActivityIdAndUserIdOrderByIdDesc(activityId, userId, PageRequest.of(0, MAX_RECORDS))
                .stream()
                .map(r -> new DrawRecordView(r.getPrizeId(), names.getOrDefault(r.getPrizeId(), ""),
                        r.getResult(), r.getCreatedAt()))
                .toList();
    }
}
