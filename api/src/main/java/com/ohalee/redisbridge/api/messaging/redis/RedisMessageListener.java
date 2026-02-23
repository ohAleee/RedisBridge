package com.ohalee.redisbridge.api.messaging.redis;

import com.ohalee.redisbridge.api.messaging.request.RequestReceptionHandler;
import com.ohalee.redisbridge.api.messaging.response.ResponseReceptionHandler;
import io.lettuce.core.pubsub.RedisPubSubListener;

/**
 * Listener interface for Redis pub/sub message events.
 *
 * <p>
 * This interface defines methods for handling messages received through Redis
 * pub/sub channels. Implementations should process incoming messages and
 * delegate them to the appropriate message or response processors.
 * </p>
 *
 * @see RequestReceptionHandler
 * @see ResponseReceptionHandler
 */
public interface RedisMessageListener extends RedisPubSubListener<String, String> {

    @Override
    default void message(String pattern, String channel, String message) {
    }

    @Override
    default void subscribed(String channel, long count) {
    }

    @Override
    default void psubscribed(String pattern, long count) {
    }

    @Override
    default void unsubscribed(String channel, long count) {
    }

    @Override
    default void punsubscribed(String pattern, long count) {
    }

}
