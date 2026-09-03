# Arena Progression Platform

[![Verify](https://github.com/yanfan-lin/arena-progression-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/yanfan-lin/arena-progression-platform/actions/workflows/ci.yml)

A backend application that processes simulated arena matches.

## Features

| Area              | Capability                                                                                          |
|-------------------|-----------------------------------------------------------------------------------------------------|
| Players and teams | Create players, manage team rosters, and activate or retire 3v3 and 5v5 teams                       |
| Match simulation  | Generate one match on request or run scheduled match simulations                                    |
| Event processing  | Process Kafka match events with validation, retries, dead-letter handling, and duplicate protection |
| Progression       | Store each match and update player progression and team ratings in one MySQL transaction            |
| Query APIs        | Retrieve player profiles, match details, match history, and team rankings                           |
| Redis             | Cache player profiles and serve Redis leaderboards with MySQL fallback and recovery                 |
| Leaderboard UI    | Display team rankings through an automatically refreshing leaderboard                               |

## Architecture

The simulator generates match events, while the platform processes them and stores permanent data in MySQL.

```mermaid
flowchart TB
    Client[API client] -->|Starts simulations| Simulator[Arena Simulator]
    Client -->|Manages and queries data| API[Platform REST APIs]
    Simulator -->|Creates players and teams| API
    Simulator -->|Publishes completed matches| Kafka[Kafka]
    Kafka -->|Delivers match events| Processor[Match Processor]

    Processor -->|Stores permanent data| MySQL[(MySQL)]
    Processor -->|Updates cached rankings| Redis[(Redis)]

    Browser[Web browser] --> UI[Leaderboard UI]
    UI --> Leaderboard[Leaderboard API]
    Leaderboard -->|Reads rankings| Redis
    Leaderboard -.->|Fallback| MySQL
```

## Modules

| Module                 | Responsibility                                                                                                    |
|------------------------|-------------------------------------------------------------------------------------------------------------------|
| `arena-contract`       | Shares the match event format and Kafka topic names between the simulator and platform                            |
| `progression-platform` | Manages players and teams, processes match events, stores results, and provides query APIs and the leaderboard UI |
| `arena-simulator`      | Prepares teams and publishes generated matches, either on request or on a schedule                                |

## Tech Stack

| Area                 | Technology                              |
|----------------------|-----------------------------------------|
| Backend              | Java 25, Spring Boot 4.1                |
| Messaging            | Apache Kafka 4.3                        |
| Database             | MySQL 8.4, Flyway                       |
| Cache                | Redis 7.4                               |
| Testing              | JUnit, Mockito, MockMvc, Testcontainers |
| Build and containers | Maven, Docker Compose                   |
| CI                   | GitHub Actions                          |
| UI                   | HTML, CSS, JavaScript                   |

## Local Setup

### Requirements

- Java 25
- Docker

Maven does not need to be installed because the repository includes the Maven wrapper.

### 1. Clone the repository

```bash
git clone https://github.com/yanfan-lin/arena-progression-platform.git
cd arena-progression-platform
```

### 2. Create the environment file

**Windows PowerShell**

```powershell
Copy-Item .env.example .env
```

**macOS/Linux**

```bash
cp .env.example .env
```

The default application settings match the MySQL credentials in `.env`. If you change them, pass the same username and
password to `progression-platform`.

### 3. Start MySQL, Kafka, and Redis

```bash
docker compose up -d
```

Wait until all three containers are healthy:

```bash
docker compose ps
```

### 4. Build the applications

**Windows PowerShell**

```powershell
.\mvnw.cmd clean package -DskipTests
```

**macOS/Linux**

```bash
./mvnw clean package -DskipTests
```

### 5. Start the progression platform

Open a new terminal:

```bash
java -jar progression-platform/target/progression-platform-0.0.1-SNAPSHOT.jar
```

### 6. Start the arena simulator

After the progression platform has started, open another terminal:

```bash
java -jar arena-simulator/target/arena-simulator-0.0.1-SNAPSHOT.jar
```

The progression platform starts first because the simulator calls its APIs to create players and teams.

### URLs

| Resource                   | Address                                  |
|----------------------------|------------------------------------------|
| Progression platform       | `http://localhost:8081`                  |
| Arena simulator            | `http://localhost:8082`                  |
| Leaderboard UI             | `http://localhost:8081/leaderboard.html` |
| Platform health            | `http://localhost:8081/actuator/health`  |
| Leaderboard rebuild status | `http://localhost:8081/actuator/info`    |
| Platform metrics           | `http://localhost:8081/actuator/metrics` |
| MySQL                      | `localhost:3306`                         |
| Kafka                      | `localhost:9092`                         |
| Redis                      | `localhost:6379`                         |

## Simulation Walkthrough

| Step | Request                  | Result                                                         |
|-----:|--------------------------|----------------------------------------------------------------|
|    1 | Set up teams and players | Creates any teams and players missing from the requested total |
|    2 | Start a simulation run   | Generates and publishes matches at the requested interval      |
|    3 | Check the current run    | Shows the run state and number of matches published            |
|    4 | Open the leaderboard     | Displays ranking updates as matches are processed              |
|    5 | Stop the run if needed   | Prevents any additional matches from being generated           |

### 1. Set up teams

Set up teams with `POST`:

```text
http://localhost:8082/api/v1/simulations/setup
```

Use `Content-Type: application/json` with this body:

```json
{
  "mode": "THREE_VS_THREE",
  "targetTeamCount": 30
}
```

The response shows the existing team count and how many teams and players were added.

### 2. Start a simulation run

Start the run with `POST`:

```text
http://localhost:8082/api/v1/simulations/runs
```

Use this JSON body:

```json
{
  "mode": "THREE_VS_THREE",
  "intervalMs": 1000,
  "maxMatches": 30
}
```

The simulator returns `202 Accepted` and begins publishing one match approximately every second.

### 3. Check the run

Check its progress with `GET`:

```text
http://localhost:8082/api/v1/simulations/runs/current
```

The response shows the run status, progress, and latest event and match IDs.

### 4. View the leaderboard

Open the leaderboard in a browser:

```text
http://localhost:8081/leaderboard.html
```

Select `3v3` to view the teams updated by the simulation. The leaderboard refreshes automatically.

### 5. Stop the run

A run stops automatically after publishing its configured number of matches. To stop it earlier, send a `DELETE` request
to:

```text
http://localhost:8082/api/v1/simulations/runs/current
```

Use `FIVE_VS_FIVE` instead of `THREE_VS_THREE` in both POST requests to simulate 5v5 matches.

## Kafka Processing

| Event outcome                | Platform behavior                                      | Kafka offset                                       |
|------------------------------|--------------------------------------------------------|----------------------------------------------------|
| Valid event                  | Store the match and progression updates                | Committed                                          |
| Duplicate event              | Skip duplicate updates                                 | Committed                                          |
| Invalid event                | Send it to the dead-letter topic                       | Committed after the dead-letter event is published |
| Retryable failure            | Retry up to three times with increasing delays         | Committed if a retry succeeds                      |
| Retries exhausted            | Send it to the dead-letter topic and stop the listener | Not committed                                      |
| Dead-letter publishing fails | Stop the listener                                      | Not committed                                      |

## API Reference

### Progression Platform

Base URL: `http://localhost:8081`

| Method | Endpoint                                   | Purpose                      |
|--------|--------------------------------------------|------------------------------|
| `POST` | `/api/v1/players`                          | Create a player              |
| `GET`  | `/api/v1/players/{playerId}`               | Get a player profile         |
| `POST` | `/api/v1/players/{playerId}/retire`        | Retire a player              |
| `GET`  | `/api/v1/players/{playerId}/matches`       | Get a player’s match history |
| `POST` | `/api/v1/teams`                            | Create a team                |
| `GET`  | `/api/v1/teams/{teamId}`                   | Get a team                   |
| `PUT`  | `/api/v1/teams/{teamId}/roster`            | Replace a team roster        |
| `POST` | `/api/v1/teams/{teamId}/activate`          | Activate a team              |
| `POST` | `/api/v1/teams/{teamId}/retire`            | Retire a team                |
| `GET`  | `/api/v1/teams/{teamId}/matches`           | Get a team’s match history   |
| `GET`  | `/api/v1/matches/{matchId}`                | Get match details            |
| `GET`  | `/api/v1/leaderboards/teams`               | Get a team leaderboard       |
| `GET`  | `/api/v1/leaderboards/teams/{teamId}/rank` | Get a team’s exact rank      |

Leaderboard queries support:

- Modes: `THREE_VS_THREE`, `FIVE_VS_FIVE`
- Ranking metrics: `RATING`, `WINS`, `WIN_RATE`
- Limits: `10`, `30`, `50`, `100`

Player and team match histories support `page` and `size` query parameters.

### Arena Simulator

Base URL: `http://localhost:8082`

| Method   | Endpoint                                  | Purpose                                     |
|----------|-------------------------------------------|---------------------------------------------|
| `POST`   | `/api/v1/simulations/setup`               | Create teams and players for one arena mode |
| `POST`   | `/api/v1/simulations/matches?mode={mode}` | Generate and publish one match              |
| `POST`   | `/api/v1/simulations/runs`                | Start scheduled match generation            |
| `GET`    | `/api/v1/simulations/runs/current`        | Get the current or most recent run          |
| `DELETE` | `/api/v1/simulations/runs/current`        | Stop the current run                        |

## Kafka and Redis Dashboards

Use these optional dashboards to view published match events and leaderboard data while a simulation is running.

Start them with:

```bash
docker compose --profile tools up -d
```

| Tool          | URL                     | Purpose                                                                     |
|---------------|-------------------------|-----------------------------------------------------------------------------|
| Kafbat UI     | `http://localhost:8080` | Inspect Kafka topics, match events, dead-letter events, and consumer groups |
| Redis Insight | `http://localhost:5540` | Inspect cached player profiles and leaderboard sorted sets                  |
