package com.personal.finance.common.api;

import org.springframework.http.HttpStatus;

/**
 * Central catalogue of application error codes.
 * Every API failure is described by one of these codes so that clients can
 * react programmatically instead of parsing human readable messages.
 */
public enum ErrorCode {

    VALIDATION_ERROR("FIN-1001", HttpStatus.BAD_REQUEST),
    NOT_FOUND("FIN-1002", HttpStatus.NOT_FOUND),
    CONFLICT("FIN-1003", HttpStatus.CONFLICT),
    UNAUTHORIZED("FIN-1004", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FIN-1005", HttpStatus.FORBIDDEN),
    PAYLOAD_TOO_LARGE("FIN-1006", HttpStatus.PAYLOAD_TOO_LARGE),

    INSUFFICIENT_BALANCE("FIN-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    CREDIT_LIMIT_EXCEEDED("FIN-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_ACCOUNT_TRANSFER("FIN-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_TYPE_MISMATCH("FIN-2004", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_AMOUNT("FIN-2005", HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_IN_USE("FIN-2006", HttpStatus.CONFLICT),
    CATEGORY_IN_USE("FIN-2007", HttpStatus.CONFLICT),
    CATEGORY_CYCLE("FIN-2008", HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_BUDGET("FIN-2009", HttpStatus.CONFLICT),
    INVALID_DATE_RANGE("FIN-2010", HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_ARCHIVED("FIN-2011", HttpStatus.UNPROCESSABLE_ENTITY),
    EXCHANGE_RATE_UNAVAILABLE("FIN-2012", HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_POLICY_VIOLATION("FIN-2013", HttpStatus.UNPROCESSABLE_ENTITY),

    IMPORT_FAILED("FIN-3001", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
