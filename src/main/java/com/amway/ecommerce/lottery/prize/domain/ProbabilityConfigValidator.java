package com.amway.ecommerce.lottery.prize.domain;

import com.amway.ecommerce.lottery.common.exception.InvalidProbabilityConfigException;
import java.util.Collection;

/**
 * Enforces the core invariant of the lottery: every prize probability (including
 * 銘謝惠顧) is non-negative and together they sum to exactly 10000 (= 100%).
 */
public final class ProbabilityConfigValidator {

    public static final int TOTAL = 10_000;

    private ProbabilityConfigValidator() {
    }

    public static void validate(Collection<Integer> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            throw new InvalidProbabilityConfigException("至少需要一個獎品設定");
        }
        int sum = 0;
        for (Integer p : probabilities) {
            if (p == null || p < 0) {
                throw new InvalidProbabilityConfigException("機率不可為負數");
            }
            sum += p;
        }
        if (sum != TOTAL) {
            throw new InvalidProbabilityConfigException(
                    "所有獎品機率總和必須為 " + TOTAL + " (目前為 " + sum + ")");
        }
    }
}
