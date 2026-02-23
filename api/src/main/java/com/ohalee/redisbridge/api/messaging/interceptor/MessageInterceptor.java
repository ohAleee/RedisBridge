package com.ohalee.redisbridge.api.messaging.interceptor;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Interceptor for messages being sent or received through the Redis bridge.
 * Interceptors can be used for logging, tracing, auditing, or modifying messages.
 */
public interface MessageInterceptor {

    /**
     * Called before a message is published.
     *
     * @param packet the packet to be sent
     * @param <M>    the message type
     * @return the packet to publish (can be modified or a new instance)
     */
    default <M extends Message> @NotNull Packet<M> onSend(@NotNull Packet<M> packet) {
        return packet;
    }

    /**
     * Called after a message is received but before it's dispatched to handlers.
     *
     * @param packet the received packet
     * @param <M>    the message type
     * @return the packet to process (can be modified or a new instance)
     */
    default <M extends Message> @NotNull Packet<M> onReceive(@NotNull Packet<M> packet) {
        return packet;
    }

}
