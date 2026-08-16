package com.yanfan.arena.contract;

// Kafka topic names shared by both the platform (consumer) and the arena simulator (producer)
public final class KafkaTopics {

    // Topic where completed match events arrive
    public static final String MATCH_COMPLETED = "arena-match-completed";

    // Topic where permanently failed match events are stored (DLT)
    public static final String MATCH_COMPLETED_DLT = "arena-match-completed-dlt";

    private KafkaTopics() {
    }

}
