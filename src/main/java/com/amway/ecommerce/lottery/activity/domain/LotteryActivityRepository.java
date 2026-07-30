package com.amway.ecommerce.lottery.activity.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotteryActivityRepository extends JpaRepository<LotteryActivity, Long> {

    Optional<LotteryActivity> findByCode(String code);

    List<LotteryActivity> findByStatus(ActivityStatus status);
}
