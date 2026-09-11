package com.amway.ecommerce.lottery.draw.application;

import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.common.exception.ActivityNotOpenException;
import com.amway.ecommerce.lottery.common.exception.DrawLimitExceededException;
import com.amway.ecommerce.lottery.common.exception.ResourceNotFoundException;
import com.amway.ecommerce.lottery.draw.domain.DrawRecord;
import com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository;
import com.amway.ecommerce.lottery.draw.domain.DrawResult;
import com.amway.ecommerce.lottery.draw.domain.WeightedOption;
import com.amway.ecommerce.lottery.draw.domain.WeightedRandomPicker;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import com.amway.ecommerce.lottery.riskcontrol.RiskControlPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrawService {

    private final LotteryActivityRepository activityRepository;
    private final PrizeRepository prizeRepository;
    private final DrawRecordRepository drawRecordRepository;
    private final RiskControlPort riskControl;
    private final WeightedRandomPicker picker;

    public DrawService(LotteryActivityRepository activityRepository,
                       PrizeRepository prizeRepository,
                       DrawRecordRepository drawRecordRepository,
                       RiskControlPort riskControl,
                       WeightedRandomPicker picker) {
        this.activityRepository = activityRepository;
        this.prizeRepository = prizeRepository;
        this.drawRecordRepository = drawRecordRepository;
        this.riskControl = riskControl;
        this.picker = picker;
    }

    /**
     * Draw {@code times} times for a user in a single request.
     *
     * <p>Correctness model: the DB conditional decrement is the source of truth
     * that makes over-draw impossible; Redis pre-deduction just keeps hot prizes
     * from hammering the DB. A picked-but-sold-out prize degrades to 銘謝惠顧.
     */
    @Transactional
    public DrawBatchResult draw(long activityId, long userId, int times, String requestKey) {
        // 1) idempotent replay: same request key returns the original outcome. 查 draw_record 表 idempotencyKey 前綴，有記錄直接回放結果
        List<DrawRecord> existing = drawRecordRepository.findByIdempotencyKeyStartingWith(requestKey + "#");
        if (!existing.isEmpty()) {
            return replay(activityId, existing);
        }

        LotteryActivity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new ResourceNotFoundException("活動不存在: " + activityId));
        if (!activity.isOpenForDraw()) {
            throw new ActivityNotOpenException("活動目前未開放抽獎");
        }

        // 2) reserve against the activity-wide cap (if the activity sets one). 活動總量預扣（若有設上限）
        Long totalLimit = activity.getTotalDrawLimit(); // 活動可能「沒有設整體上限」，也不限制整體抽獎次數，所以是nullable
        boolean activityReserved = false;
        if (totalLimit != null) {
            if (!riskControl.tryReserveActivityTotal(activityId, totalLimit, times)) {
                throw new DrawLimitExceededException("本活動整體抽獎次數已達上限");
            }
            activityReserved = true;    // 只有真的呼叫了預扣，才標記 true
        }

        // 3) reserve the user's draw quota atomically (per-user limit guard). 個人配額預扣
        if (!riskControl.tryReserveUserQuota(activityId, userId, activity.getPerUserDrawLimit(), times)) {
            if (activityReserved) {
                riskControl.releaseActivityTotal(activityId, times);        // 只有預扣過才需要釋放
            }
            throw new DrawLimitExceededException("已達個人抽獎次數上限");
        }

        try {
            return runDraws(activityId, userId, times, requestKey);
        } catch (RuntimeException ex) {
            // release the reserved quota so a failed request doesn't burn the user's chances
            riskControl.releaseUserQuota(activityId, userId, times);        // 個人配額一定有預扣過，無條件釋放
            if (activityReserved) {
                riskControl.releaseActivityTotal(activityId, times);        // 活動總量看旗標，有預扣過才需要釋放
            }
            throw ex;
        }
    }

    private DrawBatchResult runDraws(long activityId, long userId, int times, String requestKey) {
        // ① 一次撈全部獎品
        List<Prize> prizes = prizeRepository.findByActivityId(activityId);
        // ② 建立這次連抽共用的機率分布
        List<WeightedOption<Prize>> options = prizes.stream()
            .map(p -> new WeightedOption<>(p, p.getProbability()))
            .toList();
        // ③ 先找好銀謝惠顧當備援
        Prize thanks = prizes.stream().filter(Prize::isThanks).findFirst().orElse(null);

        List<DrawOutcome> outcomes = new ArrayList<>(times);
        // ④ 迴圈跑 times 次，逐次抽
        for (int i = 0; i < times; i++) {
            // ⑤ 加權隨機選一個候選
            Prize candidate = picker.pick(options).value();
            Prize awarded;
            DrawResult result;
            // ⑥ 選到銀謝惠顧，直接定案（不消耗庫存）
            if (candidate.isThanks()) {
                awarded = candidate;
                result = DrawResult.THANKS;
            } else {
                // ⑦ Redis 原子預扣（快篩）
                boolean reserved = riskControl.tryDeductStock(candidate.getId());
                if (reserved && prizeRepository.decrementStock(candidate.getId()) == 1) {
                    // ⑧ Redis 跟 DB 都同意(result=1) → 真中獎
                    awarded = candidate;
                    result = DrawResult.WIN;
                } else {
                    // Redis allowed it but the DB is already empty -> put the unit back.
                    // ⑨ Redis 同意但 DB 說沒了 → 補回 Redis
                    if (reserved) {
                        riskControl.restoreStock(candidate.getId());
                    }
                    // ⑩ 降級銀謝惠顧
                    awarded = thanks != null ? thanks : candidate;
                    result = DrawResult.THANKS;
                }
            }
            // ⑪ 每一抽各寫一筆紀錄
            drawRecordRepository.save(new DrawRecord(activityId, userId, awarded.getId(),
                result, requestKey + "#" + i));
            // TODO: 中獎後改發 MQ 事件（出貨 / 通知），把副作用從抽獎主流程解耦
            outcomes.add(new DrawOutcome(awarded.getId(), awarded.getName(), result));
        }
        return new DrawBatchResult(times, outcomes);
    }

    private DrawBatchResult replay(long activityId, List<DrawRecord> records) {
        Map<Long, String> names = prizeRepository.findByActivityId(activityId).stream()
            .collect(Collectors.toMap(Prize::getId, Prize::getName));
        List<DrawOutcome> outcomes = records.stream()
            .sorted((a, b) -> a.getIdempotencyKey().compareTo(b.getIdempotencyKey()))
            .map(r -> new DrawOutcome(r.getPrizeId(), names.getOrDefault(r.getPrizeId(), ""), r.getResult()))
            .collect(Collectors.toList());
        return new DrawBatchResult(outcomes.size(), outcomes);
    }
}
