package com.yanfan.arena.platform.leaderboard.redis;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

// Coordinate leaderboard rebuilds and Redis updates after commits
@Component
public class TeamLeaderboardProjectionLock {

    // Prevent a rebuild from overwriting a team update
    // that committed at the same time.
    private final ReentrantLock lock = new ReentrantLock();

    public void lock() { lock.lock(); }

    public void unlock() { lock.unlock(); }

}
