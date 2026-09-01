package com.yanfan.arena.platform.match.validation;

import com.yanfan.arena.platform.error.ApiException;

// Report a match event that fails validation or safe progression limits.
public class MatchEventValidationException extends ApiException {

    public MatchEventValidationException(String message) {

        super("INVALID_MATCH_EVENT", message);
    }

}
