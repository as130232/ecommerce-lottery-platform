package com.amway.ecommerce.lottery.riskcontrol;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis-backed risk control. Each check runs as a single Lua script so the
 * read-decide-write is atomic even with many app instances hitting the same
 * Redis. See {@link RiskControlPort} for the correctness contract.
 */
@Service
public class RedisRiskControlService implements RiskControlPort {

    // reserve N draws only if it won't exceed the limit
    private static final RedisScript<Long> RESERVE_QUOTA = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            local times = tonumber(ARGV[2])
            if current + times > limit then
                return 0
            end
            redis.call('INCRBY', KEYS[1], times)
            return 1
            """, Long.class);

    private static final RedisScript<Long> RELEASE_QUOTA = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local next = current - tonumber(ARGV[1])
            if next < 0 then next = 0 end
            redis.call('SET', KEYS[1], next)
            return next
            """, Long.class);

    // returns 1 = reserved, 0 = sold out, -1 = counter not initialised (cold cache)
    private static final RedisScript<Long> DEDUCT_STOCK = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if stock == false then
                return -1
            end
            if tonumber(stock) <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return 1
            """, Long.class);

    private static final RedisScript<Long> RESTORE_STOCK = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('INCR', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRiskControlService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryReserveUserQuota(long activityId, long userId, int perUserLimit, int times) {
        Long ok = redis.execute(RESERVE_QUOTA, List.of(quotaKey(activityId, userId)),
                String.valueOf(perUserLimit), String.valueOf(times));
        return ok != null && ok == 1L;
    }

    @Override
    public void releaseUserQuota(long activityId, long userId, int times) {
        redis.execute(RELEASE_QUOTA, List.of(quotaKey(activityId, userId)), String.valueOf(times));
    }

    @Override
    public boolean tryReserveActivityTotal(long activityId, long totalLimit, int times) {
        Long ok = redis.execute(RESERVE_QUOTA, List.of(activityTotalKey(activityId)),
                String.valueOf(totalLimit), String.valueOf(times));
        return ok != null && ok == 1L;
    }

    @Override
    public void releaseActivityTotal(long activityId, int times) {
        redis.execute(RELEASE_QUOTA, List.of(activityTotalKey(activityId)), String.valueOf(times));
    }

    @Override
    public boolean tryDeductStock(long prizeId) {
        Long result = redis.execute(DEDUCT_STOCK, List.of(stockKey(prizeId)));
        // 1 = reserved; -1 = cold cache -> optimistically allow, DB decides for real
        return result != null && (result == 1L || result == -1L);
    }

    @Override
    public void restoreStock(long prizeId) {
        redis.execute(RESTORE_STOCK, List.of(stockKey(prizeId)));
    }

    @Override
    public void initStock(long prizeId, int remainingStock) {
        redis.opsForValue().set(stockKey(prizeId), String.valueOf(remainingStock));
    }

    private static String quotaKey(long activityId, long userId) {
        return "lottery:quota:" + activityId + ":" + userId;
    }

    private static String stockKey(long prizeId) {
        return "lottery:stock:" + prizeId;
    }

    private static String activityTotalKey(long activityId) {
        return "lottery:total:" + activityId;
    }
}
