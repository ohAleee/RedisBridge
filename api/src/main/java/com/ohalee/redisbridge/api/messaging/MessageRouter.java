package com.ohalee.redisbridge.api.messaging;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.messaging.response.Response;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for routing messages between different entities in the Redis bridge system.
 * Handles both fire-and-forget messaging and request-response patterns.
 */
public interface MessageRouter {

    /**
     * Initializes the message router and prepares it for message handling.
     * This method should be called before any message operations.
     */
    void load();

    /**
     * Shuts down the message router and releases any resources.
     * This method should be called when the router is no longer needed.
     */
    void unload();

    /**
     * Publishes a message to a specific receiver without expecting a response.
     * This method publishes the message instantly.
     *
     * @param message  the message to publish
     * @param receiver the entity that should receive the message
     * @param <M>      the message type
     * @return a completion stage containing the full message that was published
     */
    <M extends Message> CompletableFuture<Packet<M>> publish(@NotNull M message, @NotNull MessageEntity receiver);

    /**
     * Queues a message for batched publishing to a specific receiver without expecting a response.
     * Messages are published periodically in batches to reduce Redis connection overhead.
     *
     * @param message  the message to publish
     * @param receiver the entity that should receive the message
     * @param <M>      the message type
     * @return a completion stage containing the full message that was published, completed when the batch is sent
     */
    <M extends Message> CompletableFuture<Packet<M>> publishQueued(@NotNull M message, @NotNull MessageEntity receiver);

    /**
     * Publishes a message and waits for a response from the receiver.
     *
     * @param message  the message to publish
     * @param receiver the entity that should receive the message and send a response
     * @param <M>      the message type
     * @param <R>      the response type
     * @return a future containing the full message response
     */
    <M extends Message, R extends Response> CompletableFuture<PacketResponse<M, R>> waitResponse(@NotNull M message, @NotNull MessageEntity receiver);

    /**
     * Publishes a message and waits for multiple responses from the receivers.
     * Note that this will only wait for responses from clients who received the message.
     * If includeSender is false, the sender will be excluded from the response count.
     * This method is useful for broadcasting a message to multiple clients and collecting
     * their responses without needing to know the exact number of recipients in advance.
     *
     * @param message       the message to publish
     * @param receiver      the entity that should receive the message and send a response
     * @param includeSender whether to include the sender in the response count
     * @param <M>           the message type
     * @param <R>           the response type
     * @return a future containing a list of full message responses
     */
    <M extends Message, R extends Response> CompletableFuture<List<PacketResponse<M, R>>> waitResponses(@NotNull M message, @NotNull MessageEntity receiver, boolean includeSender);

    /**
     * Publishes a complete message response to a receiver.
     *
     * @param messageResponse the full message response to publish
     * @param receiver        the entity that should receive the response
     * @param <M>             the original message type
     * @param <R>             the response type
     */
    <M extends Message, R extends Response> void publishResponse(@NotNull PacketResponse<M, R> messageResponse, @NotNull MessageEntity receiver);

    /**
     * Publishes a response to an original message.
     *
     * @param original the original message being responded to
     * @param response the response to send
     * @param receiver the entity that should receive the response
     * @param <M>      the original message type
     * @param <R>      the response type
     */
    <M extends Message, R extends Response> void publishResponse(@NotNull Packet<M> original, @NotNull R response, @NotNull MessageEntity receiver);

    /**
     * Convenience method to reply to a message, automatically routing the response back to the original sender.
     *
     * @param original the original message being responded to
     * @param response the response to send back
     * @param <M>      the original message type
     * @param <R>      the response type
     */
    default <M extends Message, R extends Response> void reply(Packet<M> original, R response) {
        this.publishResponse(original, response, MessageEntity.response(original.sender()));
    }

    /**
     * Settings for configuring the behavior of the MessageRouter.
     *
     * @param activeQueueExecutor     whether to enable the queued message executor
     * @param queuePublishDelayMillis the delay in milliseconds between queued message batch publications
     * @param ackTimeoutSeconds       the timeout in seconds for acknowledging messages
     * @param responseTimeoutSeconds  the timeout in seconds for waiting for message responses
     */
    record Settings(boolean activeQueueExecutor, int queuePublishDelayMillis, int ackTimeoutSeconds,
                    int responseTimeoutSeconds) {

        public static final int DEFAULT_QUEUE_DELAY_MILLIS = 100;
        public static final int DEFAULT_ACK_TIMEOUT_SECONDS = 5;
        public static final int DEFAULT_RESPONSE_TIMEOUT_SECONDS = 15;

        public Settings() {
            this(true, DEFAULT_QUEUE_DELAY_MILLIS, DEFAULT_ACK_TIMEOUT_SECONDS, DEFAULT_RESPONSE_TIMEOUT_SECONDS);
        }

        /**
         * Provides default settings for the MessageRouter.
         *
         * @return default Settings instance
         */
        public static Settings defaultSettings() {
            return new Settings();
        }
    }
}
