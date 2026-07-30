package com.amway.ecommerce.lottery.common.exception;

public class DrawLimitExceededException extends BusinessException {

    public DrawLimitExceededException(String message) {
        super(ErrorCode.DRAW_LIMIT_EXCEEDED, message);
    }
}
