package it.ohalee.redisbridge.api.messaging.redis;

import io.lettuce.core.pubsub.RedisPubSubListener;
import it.ohalee.redisbridge.api.messaging.request.MessageDeserializer;
import it.ohalee.redisbridge.api.messaging.response.ResponseDeserializer;

/**
 * Listener interface for Redis pub/sub message events.
 *
 * <p>This interface defines callbacks for handling messages received through Redis
 * pub/sub channels. Implementations should process incoming messages and delegate
 * them to the appropriate message or response processors.</p>
 *
 * @see MessageDeserializer
 * @see ResponseDeserializer
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
