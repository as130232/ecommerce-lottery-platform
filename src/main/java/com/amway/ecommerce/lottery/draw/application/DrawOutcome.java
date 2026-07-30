package com.amway.ecommerce.lottery.draw.application;

import com.amway.ecommerce.lottery.draw.domain.DrawResult;

/** Result of a single draw within a (possibly multi-) draw request. */
public record DrawOutcome(Long prizeId, String prizeName, DrawResult result) {
}
