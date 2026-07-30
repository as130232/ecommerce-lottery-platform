package com.amway.ecommerce.lottery.draw.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.common.exception.DrawLimitExceededException;
import com.amway.ecommerce.lottery.draw.domain.DrawRecord;
import com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository;
import com.amway.ecommerce.lottery.draw.domain.DrawResult;
import com.amway.ecommerce.lottery.draw.domain.WeightedRandomPicker;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import com.amway.ecommerce.lottery.prize.domain.PrizeType;
import com.amway.ecommerce.lottery.riskcontrol.RiskControlPort;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DrawServiceTest {

    private LotteryActivityRepository activityRepository;
    private PrizeRepository prizeRepository;
    private DrawRecordRepository drawRecordRepository;
    private RiskControlPort riskControl;

    private Prize realPrize;
    private Prize thanksPrize;
    private LotteryActivity activity;

    @BeforeEach
    void setUp() {
        activityRepository = org.mockito.Mockito.mock(LotteryActivityRepository.class);
        prizeRepository = org.mockito.Mockito.mock(PrizeRepository.class);
        drawRecordRepository = org.mockito.Mockito.mock(DrawRecordRepository.class);
        riskControl = org.mockito.Mockito.mock(RiskControlPort.class);

        realPrize = new Prize(1L, "頭獎", PrizeType.PRIZE, 5_000, 3);
        ReflectionTestUtils.setField(realPrize, "id", 1L);
        thanksPrize = new Prize(1L, "銘謝惠顧", PrizeType.THANKS, 5_000, 0);
        ReflectionTestUtils.setField(thanksPrize, "id", 2L);

        activity = new LotteryActivity("ACT", "測試活動", 10);
        ReflectionTestUtils.setField(activity, "id", 1L);
        activity.setStatus(ActivityStatus.ACTIVE);
    }

    /** Picker forced to always select the first option (the real prize). */
    private DrawService serviceThatPicksRealPrize() {
        IntUnaryOperator alwaysZero = bound -> 0;
        return new DrawService(activityRepository, prizeRepository, drawRecordRepository,
                riskControl, new WeightedRandomPicker(alwaysZero));
    }

    @Test
    void winsAndDecrementsStockWhenPrizeAvailable() {
        when(drawRecordRepository.findByIdempotencyKeyStartingWith(any())).thenReturn(List.of());
        when(activityRepository.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(prizeRepository.findByActivityId(1L)).thenReturn(List.of(realPrize, thanksPrize));
        when(riskControl.tryReserveUserQuota(anyLong(), anyLong(), anyInt(), eq(1))).thenReturn(true);
        when(riskControl.tryDeductStock(1L)).thenReturn(true);
        when(prizeRepository.decrementStock(1L)).thenReturn(1);

        DrawBatchResult result = serviceThatPicksRealPrize().draw(1L, 99L, 1, "req-1");

        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().get(0).result()).isEqualTo(DrawResult.WIN);
        assertThat(result.outcomes().get(0).prizeId()).isEqualTo(1L);
        verify(prizeRepository).decrementStock(1L);
    }

    @Test
    void degradesToThanksWhenPrizeSoldOut() {
        when(drawRecordRepository.findByIdempotencyKeyStartingWith(any())).thenReturn(List.of());
        when(activityRepository.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(prizeRepository.findByActivityId(1L)).thenReturn(List.of(realPrize, thanksPrize));
        when(riskControl.tryReserveUserQuota(anyLong(), anyLong(), anyInt(), eq(1))).thenReturn(true);
        when(riskControl.tryDeductStock(1L)).thenReturn(false); // Redis says sold out

        DrawBatchResult result = serviceThatPicksRealPrize().draw(1L, 99L, 1, "req-2");

        assertThat(result.outcomes().get(0).result()).isEqualTo(DrawResult.THANKS);
        assertThat(result.outcomes().get(0).prizeId()).isEqualTo(2L); // the THANKS prize
        verify(prizeRepository, never()).decrementStock(anyLong());

        ArgumentCaptor<DrawRecord> captor = ArgumentCaptor.forClass(DrawRecord.class);
        verify(drawRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(DrawResult.THANKS);
    }

    @Test
    void rejectsWhenUserQuotaExceeded() {
        when(drawRecordRepository.findByIdempotencyKeyStartingWith(any())).thenReturn(List.of());
        when(activityRepository.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(riskControl.tryReserveUserQuota(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(false);

        DrawService service = serviceThatPicksRealPrize();
        assertThatThrownBy(() -> service.draw(1L, 99L, 3, "req-3"))
                .isInstanceOf(DrawLimitExceededException.class);

        verify(drawRecordRepository, never()).save(any());
        verify(riskControl, never()).tryDeductStock(anyLong());
    }

    @Test
    void replaysIdempotentRequestWithoutDrawingAgain() {
        DrawRecord prior = new DrawRecord(1L, 99L, 1L, DrawResult.WIN, "req-4#0");
        when(drawRecordRepository.findByIdempotencyKeyStartingWith("req-4#")).thenReturn(List.of(prior));
        when(prizeRepository.findByActivityId(1L)).thenReturn(List.of(realPrize, thanksPrize));

        DrawBatchResult result = serviceThatPicksRealPrize().draw(1L, 99L, 1, "req-4");

        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().get(0).prizeId()).isEqualTo(1L);
        verifyNoInteractions(riskControl);
        verify(activityRepository, never()).findById(anyLong());
        verify(drawRecordRepository, never()).save(any());
    }
}
