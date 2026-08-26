package com.yanfan.arena.platform.error;

// Return 400 when receiving an invalid request.
public class BadRequestException extends ApiException{

    public BadRequestException(String code, String message) {
        super(code, message);
    }

}
