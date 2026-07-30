package com.amway.ecommerce.lottery.riskcontrol;

/**
 * Fast, atomic risk-control operations. The Redis-backed implementation keeps
 * per-user draw quota and per-prize stock as a first line of defence under high
 * concurrency; the database (conditional decrement + optimistic lock) remains the
 * final source of truth, so a Redis outage degrades throughput but never
 * correctness.
 */
public interface RiskControlPort {

    /**
     * Atomically reserve {@code times} draws for the user, rejecting the whole
     * batch if it would push the user past {@code perUserLimit}.
     *
     * @return true when the quota was reserved
     */
    boolean tryReserveUserQuota(long activityId, long userId, int perUserLimit, int times);

    /** Give back {@code times} reserved draws (compensation on failure). */
    void releaseUserQuota(long activityId, long userId, int times);

    /**
     * Atomically reserve {@code times} draws against the activity-wide cap.
     *
     * @return true when the reservation fits within {@code totalLimit}
     */
    boolean tryReserveActivityTotal(long activityId, long totalLimit, int times);

    /** Give back {@code times} activity-wide draws (compensation on failure). */
    void releaseActivityTotal(long activityId, int times);

    /**
     * Atomically deduct one unit of stock for a prize.
     *
     * @return true when a unit was reserved, false when sold out
     */
    boolean tryDeductStock(long prizeId);

    /** Give back one unit of stock (compensation when the DB disagrees). */
    void restoreStock(long prizeId);

    /** (Re)initialise the cached stock counter for a prize. */
    void initStock(long prizeId, int remainingStock);
}
