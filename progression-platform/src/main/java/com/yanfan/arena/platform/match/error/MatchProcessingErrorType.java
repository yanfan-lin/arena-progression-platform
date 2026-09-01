package com.yanfan.arena.platform.match.error;

// Represent how Kafka should handle a failed match event.
public enum MatchProcessingErrorType {

    // Kafka could not convert the record into a match event
    DESERIALIZATION,

    // Retrying the same invalid event will not fix it
    PERMANENT,

    RETRYABLE

}
