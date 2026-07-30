package com.amway.ecommerce.lottery.draw.domain;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/**
 * Picks one option using a weighted cumulative distribution. The randomness is
 * injected as a {@code bound -> [0, bound)} function so the selection is fully
 * deterministic and unit-testable.
 */
public final class WeightedRandomPicker {

    private final IntUnaryOperator rollFn;

    public WeightedRandomPicker(IntUnaryOperator rollFn) {
        this.rollFn = rollFn;
    }

    /** Picker backed by {@link ThreadLocalRandom} - safe for concurrent use. */
    public static WeightedRandomPicker threadLocal() {
        return new WeightedRandomPicker(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    public <T> WeightedOption<T> pick(List<WeightedOption<T>> options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        int total = 0;
        for (WeightedOption<T> option : options) {
            total += option.weight();
        }
        if (total <= 0) {
            throw new IllegalArgumentException("total weight must be positive");
        }

        int roll = rollFn.applyAsInt(total);
        int cursor = 0;
        for (WeightedOption<T> option : options) {
            cursor += option.weight();
            if (roll < cursor) {
                return option;
            }
        }
        // Only reachable if rollFn returns >= total (misbehaving source); fall back to last.
        return options.get(options.size() - 1);
    }
}
