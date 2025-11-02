package it.ohalee.redisbridge.api.messaging.request;

import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling received messages.
 *
 * @param <M> the message type
 *
 * @see VoidMessageHandler
 * @see Message
 */
@FunctionalInterface
public interface MessageHandler<M extends BaseMessage> {

    /**
     * Handle the incoming message
     *
     * @param message the full message containing the message and metadata
     */
    void handle(@NotNull Message<M> message);

}
