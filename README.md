# Grid07 — Spring Boot Core API & Guardrails

A robust Spring Boot 3.x microservice implementing a content platform API with Redis-based guardrails and event-driven scheduling.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.x |
| Relational DB | PostgreSQL 16 (JPA/Hibernate) |
| Cache / State | Redis 7 (Spring Data Redis) |
| Containerization | Docker + Docker Compose |

---

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

This spins up:
- PostgreSQL on `localhost:5432` (db: `grid07db`, user: `grid07user`, pass: `grid07pass`)
- Redis on `localhost:6379`

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 3. Import the Postman Collection

Import `Grid07_API.postman_collection.json` into Postman.  
Set the `baseUrl` variable to `http://localhost:8080`.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users` | Create a user |
| POST | `/api/bots` | Create a bot |
| POST | `/api/posts` | Create a post (user or bot) |
| POST | `/api/posts/{id}/comments` | Add a comment |
| POST | `/api/posts/{id}/like` | Like a post |
| GET | `/api/posts/{id}/virality` | Get virality score from Redis |

---

## Architecture Overview

```
Client → PostController / UserBotController
              ↓
         PostService / CommentService
              ↓                    ↓
     PostgreSQL (truth)     RedisGuardrailService
                                   ↓
                        Virality | Locks | Notifications
                                              ↓
                               NotificationSweeper (CRON)
```

**PostgreSQL** is the source of truth for all content (posts, comments, likes).  
**Redis** is the gatekeeper — all guardrail checks happen in Redis *before* any DB write.

---

## Phase 2: Thread Safety for Atomic Locks

### The Problem: Race Conditions

The 200-concurrent-requests stress test requires that the horizontal cap stops at **exactly** 100 bot replies. A naive approach using separate `GET` + `INCR` calls would fail:

```
Thread A: GET bot_count → 99  ✓ (under limit)
Thread B: GET bot_count → 99  ✓ (under limit)   ← race condition!
Thread A: INCR → 100
Thread B: INCR → 101          ← EXCEEDED!
```

### The Solution: Lua Script Atomicity

The horizontal cap uses a **Lua script executed server-side in Redis**. Redis processes Lua scripts in a single-threaded, atomic manner — no other commands can interleave during execution.

```lua
local current = redis.call('GET', KEYS[1])
if current and tonumber(current) >= tonumber(ARGV[1]) then
    return -1          -- cap reached, reject
end
return redis.call('INCR', KEYS[1])  -- atomic check-and-increment
```

This guarantees:
- The check and increment happen as one indivisible operation
- Under 200 concurrent requests, the counter will stop at exactly 100
- No database write is ever attempted after the 100th bot reply

### Cooldown Cap: SET NX

The 10-minute bot cooldown uses `SET key value NX PX ttl_ms` — a **single atomic Redis command** that sets a key only if it does not exist, with TTL. This is inherently race-condition-safe.

### Statelessness

All state (counters, cooldowns, pending notifications) lives exclusively in Redis. The Spring Boot application holds zero in-memory state — no `HashMap`, no `static` variables. This means the app can be horizontally scaled freely.

---

## Redis Key Schema

| Key | Type | Description |
|-----|------|-------------|
| `post:{id}:virality_score` | String (integer) | Running virality score |
| `post:{id}:bot_count` | String (integer) | Number of bot replies (atomic cap) |
| `cooldown:bot_{id}:human_{id}` | String w/ TTL | 10-min cooldown between bot and human |
| `notif_cooldown:user_{id}` | String w/ TTL | 15-min notification cooldown per user |
| `user:{id}:pending_notifs` | List | Queued notification strings |

---

## Phase 3: Notification Engine

- When a bot interacts with a user's post:
  - **No cooldown active** → log "Push Notification Sent" immediately + set 15-min cooldown
  - **Cooldown active** → push notification string into `user:{id}:pending_notifs` Redis list

- The `NotificationSweeper` `@Scheduled` task runs **every 5 minutes**, scans all pending notification keys, pops and summarises them:
  ```
  Summarized Push Notification: Bot GPT-Bot-1 and [3] others interacted with your posts.
  ```

---

## Phase 4: Edge Cases

| Test | How it's handled |
|------|-----------------|
| 200 concurrent bot comments | Lua script atomic check-and-increment stops at exactly 100 |
| Statelessness | Zero in-memory counters — all state in Redis |
| Data integrity | Redis guardrails checked before any `@Transactional` DB write |
| Depth > 20 | Checked before Redis bot_count increment (fail fast) |
| Duplicate likes | `UNIQUE` constraint on `post_likes(post_id, user_id)` |
