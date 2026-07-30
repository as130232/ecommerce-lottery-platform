package com.amway.ecommerce.lottery.common.exception;

public class InvalidProbabilityConfigException extends BusinessException {

    public InvalidProbabilityConfigException(String message) {
        super(ErrorCode.INVALID_PROBABILITY_CONFIG, message);
    }
}
