package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;

// Database access for accepted match headers.
public interface MatchResultRepository extends JpaRepository<MatchResult, String> {

}
