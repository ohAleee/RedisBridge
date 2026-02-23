package com.ohalee.redisbridge.api.messaging.request;

import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling incoming messages without producing a response.
 *
 * <p>This handler is used for fire-and-forget style messaging where no response
 * is expected or required. The handler processes the incoming message and performs
 * side effects without returning any value.</p>
 *
 * @param <M> the type of message to handle
 * @see MessageHandler
 * @see Packet
 */
@FunctionalInterface
public interface VoidMessageHandler<M extends Message> {

    /**
     * Handle the incoming message
     *
     * @param message the message to handle
     */
    void handle(@NotNull Packet<M> message);

}
