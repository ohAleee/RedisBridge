package com.ohalee.redisbridge.client.redis;

import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.concurrent.CompletableFuture;

/**
 * Centralized helper for publishing messages to Redis channels.
 *
 * <p>Publishing is routed through pooled command connections obtained from the
 * {@link RedisConnectionProvider}, rather than the shared pub/sub connection used
 * for subscriptions. This decouples publish throughput from the subscribe loop and
 * avoids the command restrictions a connection has while in RESP2 subscriber mode.</p>
 */
public class RedisPublisher {

    private final RedisConnectionProvider connectionProvider;

    public RedisPublisher(RedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Publishes a payload to the given channel using a pooled command connection,
     * returning the connection to the pool once the command completes.
     *
     * @param channel the Redis channel to publish to
     * @param payload the serialized message payload
     * @return a future completing with the number of clients that received the message
     */
    public CompletableFuture<Long> publish(String channel, String payload) {
        StatefulRedisConnection<String, String> connection;
        try {
            connection = this.connectionProvider.connection();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        return connection.async().publish(channel, payload)
                .whenComplete((count, throwable) -> this.connectionProvider.returnConnection(connection))
                .toCompletableFuture();
    }
}
