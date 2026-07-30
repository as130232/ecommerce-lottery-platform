package com.amway.ecommerce.lottery.common.exception;

public class ActivityNotOpenException extends BusinessException {

    public ActivityNotOpenException(String message) {
        super(ErrorCode.ACTIVITY_NOT_OPEN, message);
    }
}
