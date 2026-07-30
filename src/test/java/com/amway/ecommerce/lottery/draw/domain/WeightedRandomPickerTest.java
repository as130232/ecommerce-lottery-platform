package com.amway.ecommerce.lottery.draw.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class WeightedRandomPickerTest {

    private static WeightedRandomPicker withFixedRoll(int roll) {
        IntUnaryOperator fixed = bound -> roll;
        return new WeightedRandomPicker(fixed);
    }

    @Test
    void picksTheOnlyOptionWhateverTheRoll() {
        var options = List.of(new WeightedOption<>("only", 10_000));
        assertThat(withFixedRoll(0).pick(options).value()).isEqualTo("only");
        assertThat(withFixedRoll(9_999).pick(options).value()).isEqualTo("only");
    }

    @Test
    void picksByCumulativeBoundaries() {
        // A[0,2000) B[2000,5000) C[5000,10000)
        var options = List.of(
                new WeightedOption<>("A", 2_000),
                new WeightedOption<>("B", 3_000),
                new WeightedOption<>("C", 5_000));

        assertThat(withFixedRoll(0).pick(options).value()).isEqualTo("A");
        assertThat(withFixedRoll(1_999).pick(options).value()).isEqualTo("A");
        assertThat(withFixedRoll(2_000).pick(options).value()).isEqualTo("B");
        assertThat(withFixedRoll(4_999).pick(options).value()).isEqualTo("B");
        assertThat(withFixedRoll(5_000).pick(options).value()).isEqualTo("C");
        assertThat(withFixedRoll(9_999).pick(options).value()).isEqualTo("C");
    }

    @Test
    void neverPicksAZeroWeightOption() {
        var options = List.of(
                new WeightedOption<>("A", 5_000),
                new WeightedOption<>("zero", 0),
                new WeightedOption<>("B", 5_000));

        // roll exactly at the boundary where the zero-weight option sits must skip it
        assertThat(withFixedRoll(5_000).pick(options).value()).isEqualTo("B");
    }

    @Test
    void distributionStaysWithinToleranceOverManyDraws() {
        var options = List.of(
                new WeightedOption<>("grand", 200),   // 2%
                new WeightedOption<>("second", 300),  // 3%
                new WeightedOption<>("third", 500),   // 5%
                new WeightedOption<>("thanks", 9_000)); // 90%

        Random random = new Random(42);
        var picker = new WeightedRandomPicker(random::nextInt);

        int n = 200_000;
        var counts = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < n; i++) {
            String v = picker.pick(options).value();
            counts.merge(v, 1, Integer::sum);
        }

        assertThat(counts.get("thanks") / (double) n).isCloseTo(0.90, org.assertj.core.data.Offset.offset(0.01));
        assertThat(counts.get("third") / (double) n).isCloseTo(0.05, org.assertj.core.data.Offset.offset(0.01));
        assertThat(counts.get("second") / (double) n).isCloseTo(0.03, org.assertj.core.data.Offset.offset(0.01));
        assertThat(counts.get("grand") / (double) n).isCloseTo(0.02, org.assertj.core.data.Offset.offset(0.01));
    }
}
