package com.yanfan.arena.platform.match;

// Represent how a match-processing failure should be treated by the Kafka Listener.
// PERMANENT -> Retries will not help
public enum MatchProcessingErrorType {
    PERMANENT,
    RETRYABLE
}
