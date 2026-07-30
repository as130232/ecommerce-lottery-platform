package com.amway.ecommerce.lottery.draw.domain;

/**
 * A candidate in a weighted draw. {@code weight} is in basis of 10000 (萬分比).
 */
public record WeightedOption<T>(T value, int weight) {
}
