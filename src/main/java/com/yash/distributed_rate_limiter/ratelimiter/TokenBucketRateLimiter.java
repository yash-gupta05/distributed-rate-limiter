package com.yash.distributed_rate_limiter.ratelimiter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class TokenBucketRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final ConsistentHashRouter shardRouter;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final TierResolver tierResolver;
    private final MeterRegistry meterRegistry;

    @Value("${ratelimiter.fail-open:true}")
    private boolean failOpen;

    @Autowired
    public TokenBucketRateLimiter(ConsistentHashRouter shardRouter,
                                   DefaultRedisScript<List> tokenBucketScript,
                                   TierResolver tierResolver,
                                   MeterRegistry meterRegistry) {
        this.shardRouter = shardRouter;
        this.tokenBucketScript = tokenBucketScript;
        this.tierResolver = tierResolver;
        this.meterRegistry = meterRegistry;
    }

    public RateLimitResult checkLimit(String clientId) {
        Tier tier = tierResolver.resolveTier(clientId);
        String key = "ratelimit:" + clientId;
        long now = System.currentTimeMillis();

        RedisTemplate<String, Object> shard = shardRouter.getShardFor(clientId);

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = shard.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(tier.getCapacity()),
                    String.valueOf(tier.getRefillRate()),
                    String.valueOf(now),
                    String.valueOf(1)
            );

            boolean allowed = result != null && result.get(0) == 1L;
            int remainingTokens = result != null ? result.get(1).intValue() : 0;

            recordMetric(clientId, tier, allowed);
            return new RateLimitResult(allowed, remainingTokens, tier.getCapacity(), tier.getRefillRate());

        } catch (Exception e) {
            logger.error("Rate limiter backend (Redis) failure for client '{}': {}", clientId, e.getMessage());

            if (failOpen) {
                logger.warn("Fail-open mode: allowing request for '{}' despite Redis failure", clientId);
                recordMetric(clientId, tier, true);
                return new RateLimitResult(true, tier.getCapacity(), tier.getCapacity(), tier.getRefillRate());
            } else {
                logger.warn("Fail-closed mode: denying request for '{}' due to Redis failure", clientId);
                recordMetric(clientId, tier, false);
                return new RateLimitResult(false, 0, tier.getCapacity(), tier.getRefillRate());
            }
        }
    }

    private void recordMetric(String clientId, Tier tier, boolean allowed) {
        Counter.builder("ratelimiter.requests")
                .tag("outcome", allowed ? "allowed" : "denied")
                .tag("clientId", clientId)
                .tag("tier", tier.name())
                .register(meterRegistry)
                .increment();
    }
}