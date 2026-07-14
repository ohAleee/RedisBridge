package com.ohalee.redisbridge.api.redis;

import com.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * Interface for managing Redis connections and pub/sub operations.
 *
 * <p>This connector provides the low-level Redis functionality required by the
 * RedisBridge messaging system, including channel subscriptions, message publishing,
 * and connection lifecycle management.</p>
 *
 * <p><b>Lifecycle:</b></p>
 * <ol>
 *   <li>Call {@link #connect()} to establish the Redis connection</li>
 *   <li>Call {@link #disconnect()} when shutting down</li>
 *   <li>Use {@link #connection()} to get a Redis connection for executing commands</li>
 *   <li>Use {@link #pubSubConnection()} to get a pub/sub connection for subscribing and publishing messages</li>
 * </ol>
 *
 * @see RedisMessageListener
 */
public interface RedisConnectionProvider {

    /**
     * Establishes a connection to the Redis server.
     *
     * @throws RuntimeException if the connection cannot be established
     */
    void connect();

    /**
     * Closes the connection to the Redis server and releases resources.
     */
    void disconnect();

    /**
     * Retrieves a stateful Redis connection for executing commands.
     *
     * <p>Implementations backed by a connection pool return a borrowed connection
     * that should be handed back through {@link #returnConnection(StatefulRedisConnection)}
     * once the caller is done with it.</p>
     *
     * @return a {@link StatefulRedisConnection} instance
     */
    StatefulRedisConnection<String, String> connection();

    /**
     * Returns a connection previously obtained from {@link #connection()}.
     *
     * <p>Pool-backed implementations should return the connection to the pool.
     * The default implementation simply closes it, which is appropriate for
     * providers that create a fresh connection per {@link #connection()} call.</p>
     *
     * @param connection the connection to release
     */
    default void returnConnection(StatefulRedisConnection<String, String> connection) {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Retrieves a stateful Redis pub/sub connection for subscribing and publishing messages.
     *
     * @return a {@link StatefulRedisPubSubConnection} instance
     */
    StatefulRedisPubSubConnection<String, String> pubSubConnection();

}
