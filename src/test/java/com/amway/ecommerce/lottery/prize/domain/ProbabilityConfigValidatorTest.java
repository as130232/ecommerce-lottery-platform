package com.amway.ecommerce.lottery.prize.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amway.ecommerce.lottery.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProbabilityConfigValidatorTest {

    @Test
    void acceptsProbabilitiesThatSumToExactly10000() {
        assertThatCode(() -> ProbabilityConfigValidator.validate(List.of(200, 300, 500, 9_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenSumIsBelow10000() {
        assertThatThrownBy(() -> ProbabilityConfigValidator.validate(List.of(200, 300, 500, 8_000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10000");
    }

    @Test
    void rejectsWhenSumIsAbove10000() {
        assertThatThrownBy(() -> ProbabilityConfigValidator.validate(List.of(5_000, 6_000)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNegativeProbability() {
        assertThatThrownBy(() -> ProbabilityConfigValidator.validate(List.of(-100, 10_100)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsEmptyConfiguration() {
        assertThatThrownBy(() -> ProbabilityConfigValidator.validate(List.of()))
                .isInstanceOf(BusinessException.class);
    }
}
