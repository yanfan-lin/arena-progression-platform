package com.yanfan.arena.platform.error;

import org.springframework.http.HttpStatus;

// Represent a missing resource as HTTP 404.
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {

        super(HttpStatus.NOT_FOUND, code, message);
    }

}
