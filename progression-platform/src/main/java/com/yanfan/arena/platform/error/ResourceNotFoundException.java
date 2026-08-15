package com.yanfan.arena.platform.error;

// Map to 404: the requested resource does not exist.
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

}
