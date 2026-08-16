package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Verify that valid JSON becomes an ArenaMatchCompleted and
// malformed JSON is marked by a deserialization exception header
class MatchEventDeserializationTest {

    private final JacksonJsonSerializer<ArenaMatchCompleted> serializer = new JacksonJsonSerializer<>();

    private final ErrorHandlingDeserializer<ArenaMatchCompleted> deserializer = new ErrorHandlingDeserializer<>(
            new JacksonJsonDeserializer<>(ArenaMatchCompleted.class)
                    .trustedPackages("com.yanfan.arena.contract"));

    @Test
    void validJsonDeserializesToArenaMatchCompleted() {

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-15T00:00:00Z"),
                1L,
                List.of(
                        new ArenaMatchCompleted.Team(1L, List.of(
                                new ArenaMatchCompleted.Player(101L, 5, 2, 3),
                                new ArenaMatchCompleted.Player(102L, 2, 1, 1),
                                new ArenaMatchCompleted.Player(103L, 0, 0, 0))),
                        new ArenaMatchCompleted.Team(2L, List.of(
                                new ArenaMatchCompleted.Player(201L, 1, 4, 2),
                                new ArenaMatchCompleted.Player(202L, 0, 1, 1),
                                new ArenaMatchCompleted.Player(203L, 2, 2, 0)))));

        byte[] json = serializer.serialize("arena-match-completed", new RecordHeaders(), event);

        Object result = deserializer.deserialize("arena-match-completed", new RecordHeaders(), json);

        assertThat(result)
                .isInstanceOf(ArenaMatchCompleted.class);

        assertThat(((ArenaMatchCompleted) result).matchId())
                .isEqualTo(UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"));

    }

    @Test
    void malformedJsonBecomesDeserializationException() {

        byte[] badJson = "{not-valid-json".getBytes(StandardCharsets.UTF_8);

        Headers headers = new RecordHeaders();

        Object result = deserializer.deserialize("arena-match-completed", headers, badJson);

        // The failure is marked by header
        assertThat(result).isNull();

        assertThat(headers.lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER))
                .isNotNull();
    }


}
