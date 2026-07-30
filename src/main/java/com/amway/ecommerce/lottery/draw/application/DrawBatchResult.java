package com.amway.ecommerce.lottery.draw.application;

import java.util.List;

public record DrawBatchResult(int times, List<DrawOutcome> outcomes) {
}
