-- KEYS[1] = bucket key, e.g. "ratelimit:user123"
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refillRate (tokens per second)
-- ARGV[3] = now (current timestamp in milliseconds)
-- ARGV[4] = requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call("HMGET", key, "tokens", "lastRefillTimestamp")
local tokens = tonumber(bucket[1])
local lastRefillTimestamp = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    lastRefillTimestamp = now
end

local elapsedSeconds = (now - lastRefillTimestamp) / 1000
local tokensToAdd = elapsedSeconds * refillRate

tokens = math.min(capacity, tokens + tokensToAdd)

local allowed = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call("HMSET", key, "tokens", tokens, "lastRefillTimestamp", now)

local ttlSeconds = math.ceil(capacity / refillRate) + 60
redis.call("EXPIRE", key, ttlSeconds)

-- Return both the allow/deny decision AND the remaining token count,
-- so the caller can expose rate limit status to the client (e.g. via HTTP headers).
return {allowed, math.floor(tokens)}