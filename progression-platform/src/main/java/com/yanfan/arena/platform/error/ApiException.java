package com.yanfan.arena.platform.error;

// Base class for errors that map to a specific HTTP status.
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }


}
