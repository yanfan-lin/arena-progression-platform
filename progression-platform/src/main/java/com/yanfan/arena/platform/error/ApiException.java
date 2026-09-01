package com.yanfan.arena.platform.error;

import org.springframework.http.HttpStatus;

// Store the HTTP status, code, and message shared by API errors.
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    private final String code;

    protected ApiException(HttpStatus status,
                           String code,
                           String message)
    {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

}
