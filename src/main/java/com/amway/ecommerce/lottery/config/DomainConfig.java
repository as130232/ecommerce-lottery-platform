package com.amway.ecommerce.lottery.config;

import com.amway.ecommerce.lottery.draw.domain.WeightedRandomPicker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public WeightedRandomPicker weightedRandomPicker() {
        return WeightedRandomPicker.threadLocal();
    }
}
