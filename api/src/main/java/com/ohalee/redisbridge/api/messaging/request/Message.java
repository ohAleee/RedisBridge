package com.ohalee.redisbridge.api.messaging.request;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.Sender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a complete message containing the message payload along with metadata
 * about its sender and unique identifier.
 *
 * <p>This interface wraps the actual message content with additional information required for
 * routing, tracking, and responding to messages in the distributed system.</p>
 *
 * @param <M>    the type of the message payload
 *
 * @see BaseMessage
 * @see MessageEntity
 */
public interface Message<M extends BaseMessage> {

    /**
     * Retrieves the unique identifier associated with this message.
     *
     * @return a {@link UUID} representing the unique ID of the message
     */
    @NotNull UUID uniqueId();

    /**
     * Retrieves the sender information of this message.
     *
     * @return a {@link Sender} representing the sender of the message
     */
    @NotNull Sender sender();

    /**
     * Retrieves the actual message payload.
     *
     * @return the message payload of type {@code M}
     */
    @NotNull M message();

    /**
     * Indicates whether the sender requested an acknowledgement (ACK) for this message.
     * If true, the receiver should send an ACK back immediately upon receipt.
     * Default is false.
     */
    default boolean ackRequested() {
        return false;
    }

}
