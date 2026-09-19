package com.yash.distributed_rate_limiter.controller;

import com.yash.distributed_rate_limiter.ratelimiter.ConsistentHashRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final ConsistentHashRouter shardRouter;

    @Autowired
    public TestController(ConsistentHashRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    @GetMapping("/api/test")
    public String testEndpoint() {
        return "Request succeeded!";
    }

    /**
     * Debug-only endpoint: shows which shard a given clientId is routed to,
     * without actually consuming a token. Useful for verifying consistent
     * hashing behavior directly.
     */
    @GetMapping("/api/debug/shard")
    public String debugShard(@RequestParam String clientId) {
        int shardIndex = shardRouter.getShardIndexFor(clientId);
        return "clientId '" + clientId + "' routes to shard " + shardIndex
                + " (out of " + shardRouter.getShardCount() + " shards)";
    }
}