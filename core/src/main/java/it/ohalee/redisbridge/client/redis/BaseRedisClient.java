package it.ohalee.redisbridge.client.redis;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import it.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import org.apache.commons.pool2.ObjectPool;

public abstract class BaseRedisClient implements RedisConnectionProvider {

    protected ObjectPool<StatefulRedisConnection<String, String>> pool;
    protected StatefulRedisPubSubConnection<String, String> pubSubConnection;

    @Override
    public void disconnect() {
        this.pool.close();
        this.pubSubConnection.close();
    }

    @Override
    public StatefulRedisConnection<String, String> connection() {
        if (this.pool == null) throw new IllegalStateException("Connection pool is not initialized");
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to borrow connection from pool", e);
        }
    }

    @Override
    public StatefulRedisPubSubConnection<String, String> pubSubConnection() {
        return this.pubSubConnection;
    }
}
