package com.yash.distributed_rate_limiter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RedisConfig {

    // Read the comma-separated "host:port,host:port,..." string from application.properties
    @Value("${ratelimiter.redis-shards}")
    private String shardAddresses;

    /**
     * Creates one independent RedisTemplate per shard address.
     * Each has its own connection factory, so they are fully separate Redis clients
     * pointing at different Redis instances.
     */
    @Bean
    public List<RedisTemplate<String, Object>> shardRedisTemplates() {
        List<RedisTemplate<String, Object>> templates = new ArrayList<>();

        for (String address : shardAddresses.split(",")) {
            String[] parts = address.trim().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
            connectionFactory.afterPropertiesSet(); // initializes the connection factory immediately

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();

            templates.add(template);
        }

        return templates;
    }

    @Bean
    public DefaultRedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }
}