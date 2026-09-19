package com.yash.distributed_rate_limiter.ratelimiter;

public class RateLimitResult {

    private final boolean allowed;
    private final int remainingTokens;
    private final int capacity;
    private final double refillRate;

    public RateLimitResult(boolean allowed, int remainingTokens, int capacity, double refillRate) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getRemainingTokens() {
        return remainingTokens;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    /**
     * Rough estimate of seconds until the bucket refills to full capacity,
     * used for the X-RateLimit-Reset header.
     */
    public long secondsUntilFullRefill() {
        double tokensNeeded = capacity - remainingTokens;
        if (tokensNeeded <= 0) return 0;
        return (long) Math.ceil(tokensNeeded / refillRate);
    }
}