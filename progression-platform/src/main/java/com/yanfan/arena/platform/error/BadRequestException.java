package com.yanfan.arena.platform.error;

import org.springframework.http.HttpStatus;

// Represent an invalid request as HTTP 400.
public class BadRequestException extends ApiException{

    public BadRequestException(String code, String message) {

        super(HttpStatus.BAD_REQUEST, code, message);
    }

}
