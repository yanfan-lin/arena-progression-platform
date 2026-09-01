package com.yanfan.arena.platform.team.domain;

import com.yanfan.arena.contract.MatchMode;

// Represent the arena modes stored by the platform.
public enum ArenaMode {
    THREE_VS_THREE,
    FIVE_VS_FIVE;

    // Convert the shared event mode into the platform's stored mode
    public static ArenaMode from(MatchMode mode) {
        return switch (mode) {
            case THREE_VS_THREE -> THREE_VS_THREE;
            case FIVE_VS_FIVE -> FIVE_VS_FIVE;
        };
    }
}
