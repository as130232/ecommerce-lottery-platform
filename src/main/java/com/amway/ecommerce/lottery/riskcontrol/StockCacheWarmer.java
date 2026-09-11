package com.amway.ecommerce.lottery.riskcontrol;

import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads current prize stock into Redis on startup so the atomic pre-deduction has
 * warm counters. Draws still work before this runs (cold cache falls back to the
 * DB), this just keeps hot prizes off the database.
 */
@Component
public class StockCacheWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockCacheWarmer.class);

    private final PrizeRepository prizeRepository;
    private final RiskControlPort riskControl;

    public StockCacheWarmer(PrizeRepository prizeRepository, RiskControlPort riskControl) {
        this.prizeRepository = prizeRepository;
        this.riskControl = riskControl;
    }

    /**
     * 將DB資料緩存至redis
     */
    @Override
    public void run(ApplicationArguments args) {
        int warmed = 0;
        for (Prize prize : prizeRepository.findAll()) {
            if (!prize.isThanks()) {
                riskControl.initStock(prize.getId(), prize.getRemainingStock());
                warmed++;
            }
        }
        log.info("Warmed Redis stock counters for {} prizes", warmed);
    }
}
