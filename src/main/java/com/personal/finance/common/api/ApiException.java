package com.personal.finance.common.api;

import org.springframework.http.HttpStatus;

/**
 * Runtime exception carrying a business {@link ErrorCode}.
 * Thrown by services when a business rule is violated; the global exception
 * handler turns it into an RFC 7807 problem detail response.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.getStatus();
    }
}
