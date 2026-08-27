# Arena Progression Platform

An event-driven game backend for match processing, player progression, team ratings, and leaderboards.

## Modules

- `arena-contract` — shared versioned match event contract
- `progression-platform` — backend: match processing, progression, ratings, leaderboards
- `arena-simulator` — standalone producer of simulated completed matches

## Project Status

| Milestone | Scope                                  | Status |
|-----------|----------------------------------------|---|
| 1         | Multi-module scaffold                  | Done |
| 2         | Docker infrastructure and profiles     | Done |
| 3         | Player lifecycle                       | Done |
| 4         | Team lifecycle                         | Done |
| 5         | Progression rules and match validation | Done |
| 6         | Transactional match processing and idempotency | Done|
| 7         | Kafka consumption, retries, DLT, and redelivery | Done|
| 8         | Match details and match history | Done |
| 9         | Redis player cache and team leaderboards | Done |
| 10        | Redis leaderboard recovery and health monitoring | Done |
