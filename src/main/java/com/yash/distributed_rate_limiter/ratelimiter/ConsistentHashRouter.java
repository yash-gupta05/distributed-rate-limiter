package com.yash.distributed_rate_limiter.ratelimiter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

@Component
public class ConsistentHashRouter {

    private final List<RedisTemplate<String, Object>> shardTemplates;

    // How many points on the ring each shard occupies. More = more even load distribution.
    private static final int VIRTUAL_NODES_PER_SHARD = 100;

    // The "ring" itself: maps a position (hash value) to the index of the real shard it represents.
    // TreeMap keeps entries sorted by key, which lets us efficiently find the next position clockwise.
    private final SortedMap<Long, Integer> ring = new TreeMap<>();

    @Autowired
    public ConsistentHashRouter(List<RedisTemplate<String, Object>> shardTemplates) {
        this.shardTemplates = shardTemplates;
        buildRing();
    }

    /**
     * Populates the ring with virtual nodes for every real shard.
     * Called once at startup.
     */
    private void buildRing() {
        for (int shardIndex = 0; shardIndex < shardTemplates.size(); shardIndex++) {
            for (int v = 0; v < VIRTUAL_NODES_PER_SHARD; v++) {
                String virtualNodeKey = "shard-" + shardIndex + "-vnode-" + v;
                long position = hash(virtualNodeKey);
                ring.put(position, shardIndex);
            }
        }
    }

    /**
     * Finds the shard responsible for a given clientId by locating the
     * nearest virtual node clockwise on the ring.
     */
    public RedisTemplate<String, Object> getShardFor(String clientId) {
        int shardIndex = getShardIndexFor(clientId);
        return shardTemplates.get(shardIndex);
    }

    public int getShardIndexFor(String clientId) {
        long position = hash(clientId);

        // tailMap gives us everything >= position, i.e. everything "clockwise" from here.
        SortedMap<Long, Integer> tail = ring.tailMap(position);

        // If nothing is clockwise (we're past the last node), wrap around to the very first node on the ring.
        Long nodeKey = tail.isEmpty() ? ring.firstKey() : tail.firstKey();

        return ring.get(nodeKey);
    }

    public int getShardCount() {
        return shardTemplates.size();
    }

    /**
     * Hashes a string into a long value, used as a position on the ring.
     * Using MD5 here for a good, well-distributed spread of hash values
     * (not for any security reason — just statistical distribution quality).
     */
    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes());

            // Use the first 8 bytes of the MD5 digest as a long value.
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}