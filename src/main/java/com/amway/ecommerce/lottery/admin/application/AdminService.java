package com.amway.ecommerce.lottery.admin.application;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.CreateActivityRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.CreatePrizeRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.UpdateActivityRequest;
import com.amway.ecommerce.lottery.admin.api.AdminRequests.UpdatePrizeRequest;
import com.amway.ecommerce.lottery.admin.api.StatsView;
import com.amway.ecommerce.lottery.common.exception.BusinessException;
import com.amway.ecommerce.lottery.common.exception.ErrorCode;
import com.amway.ecommerce.lottery.common.exception.ResourceNotFoundException;
import com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository;
import com.amway.ecommerce.lottery.draw.domain.DrawResult;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import com.amway.ecommerce.lottery.prize.domain.PrizeType;
import com.amway.ecommerce.lottery.prize.domain.ProbabilityConfigValidator;
import com.amway.ecommerce.lottery.riskcontrol.RiskControlPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final LotteryActivityRepository activityRepository;
    private final PrizeRepository prizeRepository;
    private final DrawRecordRepository drawRecordRepository;
    private final RiskControlPort riskControl;

    public AdminService(LotteryActivityRepository activityRepository,
                        PrizeRepository prizeRepository,
                        DrawRecordRepository drawRecordRepository,
                        RiskControlPort riskControl) {
        this.activityRepository = activityRepository;
        this.prizeRepository = prizeRepository;
        this.drawRecordRepository = drawRecordRepository;
        this.riskControl = riskControl;
    }

    @Transactional
    public LotteryActivity createActivity(CreateActivityRequest req) {
        activityRepository.findByCode(req.code()).ifPresent(a -> {
            throw new BusinessException(ErrorCode.CONFLICT, "活動代碼已存在: " + req.code());
        });
        LotteryActivity activity = new LotteryActivity(req.code(), req.name(), req.perUserDrawLimit());
        activity.setTotalDrawLimit(req.totalDrawLimit());
        return activityRepository.save(activity);
    }

    @Transactional
    public LotteryActivity updateActivity(Long id, UpdateActivityRequest req) {
        LotteryActivity activity = getActivity(id);
        activity.setName(req.name());
        activity.setPerUserDrawLimit(req.perUserDrawLimit());
        activity.setTotalDrawLimit(req.totalDrawLimit());
        // an activity may only go live once its prize probabilities add up to 100%
        if (req.status() == ActivityStatus.ACTIVE) {
            validateProbabilities(id);
        }
        activity.setStatus(req.status());
        return activityRepository.save(activity);
    }

    @Transactional
    public Prize addPrize(Long activityId, CreatePrizeRequest req) {
        getActivity(activityId); // ensure exists
        PrizeType type = parseType(req.type());
        Prize prize = new Prize(activityId, req.name(), type, req.probability(), req.totalStock());
        Prize saved = prizeRepository.save(prize);
        if (!saved.isThanks()) {
            riskControl.initStock(saved.getId(), saved.getRemainingStock());
        }
        return saved;
    }

    @Transactional
    public Prize updatePrize(Long prizeId, UpdatePrizeRequest req) {
        Prize prize = prizeRepository.findById(prizeId)
                .orElseThrow(() -> new ResourceNotFoundException("獎品不存在: " + prizeId));
        if (req.remainingStock() > req.totalStock()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剩餘庫存不可大於總庫存");
        }
        prize.setName(req.name());
        prize.setProbability(req.probability());
        prize.adjustStock(req.totalStock(), req.remainingStock());
        Prize saved = prizeRepository.save(prize);
        // keep the Redis counter in sync with the new configuration
        if (!saved.isThanks()) {
            riskControl.initStock(saved.getId(), saved.getRemainingStock());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public StatsView stats(Long activityId) {
        LotteryActivity activity = getActivity(activityId);
        List<Prize> prizes = prizeRepository.findByActivityId(activityId);
        List<StatsView.PrizeStat> prizeStats = prizes.stream()
                .filter(p -> !p.isThanks())
                .map(p -> new StatsView.PrizeStat(p.getId(), p.getName(), p.getTotalStock(),
                        p.getRemainingStock(), p.getTotalStock() - p.getRemainingStock()))
                .toList();
        long totalDraws = drawRecordRepository.countByActivityId(activityId);
        long thanks = drawRecordRepository.countByActivityIdAndResult(activityId, DrawResult.THANKS);
        return new StatsView(activityId, activity.getCode(), totalDraws, thanks, prizeStats);
    }

    private void validateProbabilities(Long activityId) {
        List<Integer> probabilities = prizeRepository.findByActivityId(activityId).stream()
                .map(Prize::getProbability)
                .toList();
        ProbabilityConfigValidator.validate(probabilities);
    }

    private LotteryActivity getActivity(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("活動不存在: " + id));
    }

    private PrizeType parseType(String type) {
        try {
            return PrizeType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "獎品類型只能是 PRIZE 或 THANKS");
        }
    }
}
