package com.amway.ecommerce.lottery.draw.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawRecordRepository extends JpaRepository<DrawRecord, Long> {

    List<DrawRecord> findByActivityIdAndUserIdOrderByIdDesc(Long activityId, Long userId, Pageable pageable);

    /** Used for idempotent replay: all records produced by one draw request. */
    List<DrawRecord> findByIdempotencyKeyStartingWith(String prefix);

    long countByActivityIdAndUserId(Long activityId, Long userId);

    long countByActivityId(Long activityId);
}
