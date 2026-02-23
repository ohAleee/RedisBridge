package com.ohalee.redisbridge.api.messaging.request;

import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling received messages.
 *
 * @param <M> the message type
 * @see VoidMessageHandler
 * @see Packet
 */
@FunctionalInterface
public interface MessageHandler<M extends Message> {

    /**
     * Handle the incoming message
     *
     * @param message the full message containing the message and metadata
     */
    void handle(@NotNull Packet<M> message);

}
