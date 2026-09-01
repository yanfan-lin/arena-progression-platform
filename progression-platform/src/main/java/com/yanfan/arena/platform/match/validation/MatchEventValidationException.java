package com.yanfan.arena.platform.match.validation;

import com.yanfan.arena.platform.error.ApiException;
import org.springframework.http.HttpStatus;

// Report a match event that fails validation or safe progression limits.
public class MatchEventValidationException extends ApiException {

    public MatchEventValidationException(String message) {

        super(HttpStatus.BAD_REQUEST, "INVALID_MATCH_EVENT", message);
    }

}
