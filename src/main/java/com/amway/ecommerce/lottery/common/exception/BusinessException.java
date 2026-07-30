package com.amway.ecommerce.lottery.common.exception;

/**
 * Base type for expected, user-facing business errors. Carries an {@link ErrorCode}
 * so the global handler can map it to an HTTP status and a stable error code.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
