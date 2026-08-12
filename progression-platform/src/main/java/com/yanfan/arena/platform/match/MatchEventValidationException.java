package com.yanfan.arena.platform.match;

import com.yanfan.arena.platform.common.ApiException;

// Thrown when a consumed match event fails version or structural checks
public class MatchEventValidationException extends ApiException {

    public MatchEventValidationException(String message) {
        super("INVALID_MATCH_EVENT", message);
    }

}
