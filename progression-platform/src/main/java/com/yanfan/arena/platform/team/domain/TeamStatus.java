package com.yanfan.arena.platform.team.domain;

// Team lifecycle. Draft teams can change players while active and retired teams are locked.
public enum TeamStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}
