package com.yanfan.arena.platform.error;

import org.springframework.http.HttpStatus;

// Represent a request that conflicts with current state as HTTP 409.
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {

        super(HttpStatus.CONFLICT, code, message);
    }

}
