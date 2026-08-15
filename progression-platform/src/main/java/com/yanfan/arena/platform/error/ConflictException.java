package com.yanfan.arena.platform.error;

// Map to 409: the request conflicts with current state
// ex: a display name that is already taken
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

}
