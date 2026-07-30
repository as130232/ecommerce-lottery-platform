package com.amway.ecommerce.lottery.draw;

import static org.assertj.core.api.Assertions.assertThat;

import com.amway.ecommerce.lottery.activity.domain.ActivityStatus;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivity;
import com.amway.ecommerce.lottery.activity.domain.LotteryActivityRepository;
import com.amway.ecommerce.lottery.common.exception.DrawLimitExceededException;
import com.amway.ecommerce.lottery.draw.application.DrawService;
import com.amway.ecommerce.lottery.draw.domain.DrawResult;
import com.amway.ecommerce.lottery.prize.domain.Prize;
import com.amway.ecommerce.lottery.prize.domain.PrizeRepository;
import com.amway.ecommerce.lottery.prize.domain.PrizeType;
import com.amway.ecommerce.lottery.riskcontrol.RiskControlPort;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the two hard invariants under real concurrency against real MySQL + Redis:
 * never over-draw stock, and never let a user exceed their per-activity limit.
 */
@SpringBootTest
@Testcontainers
class ConcurrentDrawTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lottery");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private DrawService drawService;
    @Autowired
    private LotteryActivityRepository activityRepository;
    @Autowired
    private PrizeRepository prizeRepository;
    @Autowired
    private RiskControlPort riskControl;

    @Test
    void neverOverDrawsStockUnderConcurrency() throws Exception {
        LotteryActivity activity = new LotteryActivity("CONC-STOCK", "concurrency stock", 1_000);
        activity.setStatus(ActivityStatus.ACTIVE);
        activity = activityRepository.saveAndFlush(activity);

        int stock = 20;
        Prize hot = prizeRepository.saveAndFlush(
                new Prize(activity.getId(), "hot", PrizeType.PRIZE, 9_000, stock)); // 90% win
        prizeRepository.saveAndFlush(
                new Prize(activity.getId(), "銘謝惠顧", PrizeType.THANKS, 1_000, 0));
        riskControl.initStock(hot.getId(), stock);

        final long activityId = activity.getId();
        int attempts = 300; // far more than stock, so the guard is really exercised
        runConcurrently(attempts, i ->
                drawService.draw(activityId, 100_000 + i, 1, "stock-" + i));

        long wins = drawRecordWins(activityId);
        Prize reloaded = prizeRepository.findById(hot.getId()).orElseThrow();

        assertThat(wins).isLessThanOrEqualTo(stock);
        assertThat(reloaded.getRemainingStock()).isGreaterThanOrEqualTo(0);
        // stock accounting stays consistent: every win consumed exactly one unit
        assertThat(wins).isEqualTo(stock - reloaded.getRemainingStock());
        // with 300 attempts at 90% the stock should be fully drained
        assertThat(wins).isEqualTo(stock);
    }

    @Test
    void neverExceedsPerUserLimitUnderConcurrency() throws Exception {
        LotteryActivity activity = new LotteryActivity("CONC-USER", "concurrency user", 5);
        activity.setStatus(ActivityStatus.ACTIVE);
        activity = activityRepository.saveAndFlush(activity);

        Prize hot = prizeRepository.saveAndFlush(
                new Prize(activity.getId(), "hot", PrizeType.PRIZE, 1_000, 10_000));
        prizeRepository.saveAndFlush(
                new Prize(activity.getId(), "銘謝惠顧", PrizeType.THANKS, 9_000, 0));
        riskControl.initStock(hot.getId(), 10_000);

        long userId = 777L;
        int attempts = 50; // one user hammering, limit is 5
        long activityId = activity.getId();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                await(start);
                try {
                    drawService.draw(activityId, userId, 1, "user-" + idx);
                    succeeded.incrementAndGet();
                } catch (DrawLimitExceededException expected) {
                    rejected.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(attempts - 5);
        assertThat(drawRecordCountForUser(activityId, userId)).isEqualTo(5);
    }

    // --- helpers -------------------------------------------------------------

    private interface DrawTask {
        void run(int i);
    }

    private void runConcurrently(int attempts, DrawTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                await(start);
                task.run(idx);
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long drawRecordWins(Long activityId) {
        return drawRecordRepository().countByActivityIdAndResult(activityId, DrawResult.WIN);
    }

    private long drawRecordCountForUser(Long activityId, Long userId) {
        return drawRecordRepository().countByActivityIdAndUserId(activityId, userId);
    }

    @Autowired
    private com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository drawRecordRepository;

    private com.amway.ecommerce.lottery.draw.domain.DrawRecordRepository drawRecordRepository() {
        return drawRecordRepository;
    }
}
