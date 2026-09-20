# Distributed Rate Limiter

A production-style distributed rate limiter built with Java, Spring Boot, Redis, and Docker.

The project explores the engineering challenges behind rate limiting in distributed systems, including atomicity under concurrency, distributed state, horizontal scaling, fault tolerance, consistent hashing, tiered limits, and observability.

## Architecture

```
Client → RateLimitFilter (middleware) → ClientIdentifier (who is this?)
                                              ↓
                                    TokenBucketRateLimiter
                                              ↓
                                    ConsistentHashRouter (which shard?)
                                              ↓
                                 Redis Shard (0-3) — atomic Lua script
```

- **`RateLimitFilter`** — a Spring `OncePerRequestFilter` that intercepts every request before it reaches a controller. Rate limiting is applied globally, with zero awareness required in business logic.
- **`ClientIdentifier`** — resolves who's making the request, in priority order: API key header → client ID header → IP address fallback. Each identity source is namespaced (`apikey:`, `client:`, `ip:`) so values from different sources never collide.
- **`TokenBucketRateLimiter`** — implements the token bucket algorithm via a single atomic Redis Lua script (see below), with fail-open fault tolerance and per-client Micrometer metrics.
- **`ConsistentHashRouter`** — routes each client to one of N Redis shards using consistent hashing with virtual nodes, so resharding doesn't require nearly every key to move.

## Algorithm: token bucket

Each client has a bucket of tokens (default capacity: 10) that refills at a fixed rate (default: 2/sec). Each request consumes one token; if none are available, the request is denied (HTTP 429). Token bucket was chosen over sliding window / leaky bucket / fixed window because it explicitly allows controlled bursts — a client that's been quiet can burst up to capacity, which is generally desirable behavior.

Refill is computed lazily at request time (`elapsed_time * refill_rate`), not via a background job — this keeps the system O(1) per request with no scheduled tasks to manage.

## Why atomicity matters, and how it's guaranteed

Naive rate limiting (`GET` current count, compute in application code, `SET` new count) has a race condition: two concurrent requests can both read the same stale count and both be allowed, silently exceeding the limit. This is invisible in casual testing and only shows up under real concurrent load — exactly the conditions a rate limiter exists to handle.

**This is tested, not just asserted.** `TokenBucketConcurrencyTest` fires 50 genuinely simultaneous threads (synchronized via `CountDownLatch`, not just a fast loop) at the same client's bucket and asserts the allowed count never exceeds capacity. This test would be expected to fail intermittently against a naive GET/SET implementation.

## Distributed proof

Ran three independent instances of the app on different ports (8081-8083), simulating a load balancer by hitting them round-robin with the same client ID. The combined total across all three instances still respected the shared limit — proof the state lives in Redis, not in any single process's memory.

## Fault tolerance

If Redis is unreachable, the rate limiter **fails open** by default (configurable via `ratelimiter.fail-open`): requests are allowed through rather than the whole API going down because of a rate-limiter dependency failure. Every fallback decision is logged, and still recorded in metrics, so an outage is visible to operators even though users are unaffected. Verified by manually stopping the Redis container mid-test and confirming (a) requests still succeeded, (b) the app automatically reconnected and resumed normal limiting once Redis came back, with no restart needed.

## Horizontal scaling: consistent hashing

Naive `hash(clientId) % N` sharding has a serious flaw: changing the number of shards changes the modulus for nearly every key, causing almost all clients to remap to a different shard simultaneously — effectively resetting most rate limits at once, right as you're scaling (often during a traffic spike).

This project implements consistent hashing with 100 virtual nodes per shard instead. **Measured result:** going from 3 to 4 shards remapped only 40% of a 20-client test set (60% of clients stayed on their original shard) — versus the ~90-100% naive modulo hashing would remap. The gap between the observed 40% and the theoretical ~25% (1/4, for a 3→4 shard resize) is attributable to the small sample size; it converges toward the theoretical value with more clients or more virtual nodes.

## Tiered rate limits (free vs. premium)

Clients are assigned a `Tier` (`FREE`: 10 capacity / 2 tokens-per-sec, `PREMIUM`: 100 capacity / 20 tokens-per-sec), each with its own limits, resolved before every request and passed into the same atomic Lua script — no separate code path per tier, just different arguments.

**Simplification, stated plainly:** tier assignment is currently a hardcoded allowlist in config (`ratelimiter.premium-clients`), matched against the identity string `ClientIdentifier` already resolves (e.g. `apikey:premium-key-123`). This proves the rate-limiting logic correctly applies per-tier limits, but a real system would resolve tier from a database lookup, a JWT claim, or an entitlements service — not a static config list. Swapping `TierResolver`'s implementation is the only change needed; nothing else in the pipeline (the filter, the Lua script, the router) would need to change.

## Rate limit response headers

Every response — allowed or denied — carries `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`, following the convention used by GitHub, Stripe, and most public APIs. A denied response also carries `Retry-After`, telling the caller exactly how many seconds until at least one token is available again. This lets well-behaved clients back off proactively instead of guessing or polling blindly. Getting the remaining count out of Redis required the Lua script to return `{allowed, remainingTokens}` as a two-element table rather than a single flag — everything downstream (`RateLimitResult`, the filter) was built around that richer response.

## Observability

Exposed via Spring Boot Actuator + Micrometer at `/actuator/metrics/ratelimiter.requests`, tagged by `outcome` (allowed/denied), `clientId`, and `tier`, so allow/deny rates are queryable per-client and per-tier over HTTP without parsing logs.

A minimal live dashboard (`dashboard.html`) is included — a single self-contained HTML file with no build step or server, opened directly in a browser. It polls the metrics endpoint every 3 seconds and shows total/allowed/denied counts plus a per-client breakdown with tier and denial rate. It's a stand-in for what Prometheus/Grafana would do in a real deployment, not a replacement for them.


## Tech stack

- Java 21, Spring Boot 4
- Redis 7 (4 shards, via Docker)
- Docker Compose for full-stack local orchestration
- JUnit 5 + Mockito for testing

## Running it

```bash
docker-compose up --build -d --wait
```

This starts 4 Redis shards and the app together, wired on a shared Docker network, and blocks until every container reports healthy — so the app is genuinely ready by the time the command returns, not just started. The app is available at `http://localhost:8080`.

**Try it:**
```bash
# Normal request
curl http://localhost:8080/api/test -H "X-Client-Id: user1"

# Check which shard a client routes to
curl http://localhost:8080/api/debug/shard?clientId=user1

# Check metrics
curl http://localhost:8080/actuator/metrics/ratelimiter.requests
```

**Or just open `dashboard.html` directly in a browser** — no server, no build step — for a live view of the same data with a per-client breakdown.

## Running tests

```bash
./mvnw test
```

10 tests: unit tests for `ClientIdentifier` and `ConsistentHashRouter` (fast, isolated, mocked dependencies), plus `TokenBucketConcurrencyTest`, which requires the Redis shards to be running (`docker-compose up -d` first).

## Configuration

All in `src/main/resources/application.properties`:

Per-tier capacity and refill rate are defined in code (`Tier.java`), not config — see the tiering section above.

## What I'd do differently at larger scale

- Use Redis Cluster instead of hand-rolled shard routing, for built-in resharding and replication
- Resolve tiers from a real entitlements source (database, JWT claim, or billing service) instead of a static config allowlist
- Add per-endpoint rate limits, not just per-client (currently one set of limits applies globally per client, regardless of which endpoint they call)
- Expose metrics in Prometheus format (`/actuator/prometheus`) and visualize in Grafana, rather than the custom `dashboard.html` — deferred deliberately, since the dashboard already demonstrates the same underlying skill (reading and presenting Actuator data) with far less infrastructure
