package com.amway.ecommerce.lottery.prize.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrizeRepository extends JpaRepository<Prize, Long> {

    List<Prize> findByActivityId(Long activityId);

    /**
     * Conditional atomic decrement at the DB level - a second safety net behind
     * the Redis pre-deduction. Only decrements when stock is still available, so
     * concurrent requests can never push remaining_stock below zero.
     *
     * @return number of rows updated (1 = success, 0 = sold out)
     */
    @Modifying
    @Query("update Prize p set p.remainingStock = p.remainingStock - 1 "
            + "where p.id = :prizeId and p.remainingStock > 0")
    int decrementStock(@Param("prizeId") Long prizeId);
}
