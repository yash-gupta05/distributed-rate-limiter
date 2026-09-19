package com.yash.distributed_rate_limiter.ratelimiter;

public enum Tier {
    FREE(10, 2.0),
    PREMIUM(100, 20.0);

    private final int capacity;
    private final double refillRate;

    Tier(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }
}