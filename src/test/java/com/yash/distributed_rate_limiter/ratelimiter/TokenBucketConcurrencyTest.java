package com.yash.distributed_rate_limiter.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TokenBucketConcurrencyTest {

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @Test
    void concurrentRequests_shouldNeverExceedCapacity() throws InterruptedException {
        String clientId = "concurrency-test-user-" + System.currentTimeMillis();
        int numberOfThreads = 50;
        int capacity = 10; // matches FREE tier in Tier.java

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    RateLimitResult result = rateLimiter.checkLimit(clientId);
                    if (result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(allowedCount.get() <= capacity,
                "Expected at most " + capacity + " allowed requests (the bucket's capacity), "
                        + "but got " + allowedCount.get() + " — this indicates a race condition");
    }
}