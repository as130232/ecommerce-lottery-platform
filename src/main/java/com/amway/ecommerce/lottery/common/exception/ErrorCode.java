package com.amway.ecommerce.lottery.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    DRAW_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    OUT_OF_STOCK(HttpStatus.CONFLICT),
    INVALID_PROBABILITY_CONFIG(HttpStatus.BAD_REQUEST),
    ACTIVITY_NOT_OPEN(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
