package com.yash.distributed_rate_limiter.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ConsistentHashRouterTest {

    /**
     * Creates a router backed by `count` fake RedisTemplates.
     * The router never actually calls these templates in the tests below —
     * it only needs a list of the correct size to build its ring.
     */
    private ConsistentHashRouter routerWithShards(int count) {
        List<RedisTemplate<String, Object>> fakeShards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fakeShards.add(mock(RedisTemplate.class));
        }
        return new ConsistentHashRouter(fakeShards);
    }

    @Test
    void sameClientId_alwaysRoutesToSameShard() {
        ConsistentHashRouter router = routerWithShards(3);

        int firstLookup = router.getShardIndexFor("user1");
        int secondLookup = router.getShardIndexFor("user1");
        int thirdLookup = router.getShardIndexFor("user1");

        assertEquals(firstLookup, secondLookup);
        assertEquals(secondLookup, thirdLookup);
    }

    @Test
    void differentClientIds_canRouteToDifferentShards() {
        ConsistentHashRouter router = routerWithShards(3);

        // Generate a reasonably large sample and confirm we actually use more than one shard.
        // (Not a strict guarantee for any single client, but with enough clients, a working
        // consistent-hash ring should spread them across all available shards.)
        boolean[] shardUsed = new boolean[3];
        for (int i = 0; i < 100; i++) {
            int shard = router.getShardIndexFor("client-" + i);
            shardUsed[shard] = true;
        }

        int shardsActuallyUsed = 0;
        for (boolean used : shardUsed) {
            if (used) shardsActuallyUsed++;
        }

        assertTrue(shardsActuallyUsed > 1,
                "Expected clients to spread across multiple shards, but all landed on the same one");
    }

    @Test
    void shardIndex_isAlwaysValidForGivenShardCount() {
        ConsistentHashRouter router = routerWithShards(4);

        for (int i = 0; i < 100; i++) {
            int shard = router.getShardIndexFor("client-" + i);
            assertTrue(shard >= 0 && shard < 4,
                    "Shard index " + shard + " is out of bounds for 4 shards");
        }
    }

    @Test
    void getShardCount_reflectsConstructorInput() {
        ConsistentHashRouter router = routerWithShards(5);

        assertEquals(5, router.getShardCount());
    }
}