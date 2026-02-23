package com.ohalee.redisbridge.api.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a response containing the original message and its associated response data
 * in a request-response pattern.
 *
 * @param <M> the type of the original message, which extends the {@link Message} interface
 * @param <R> the type of the response, which extends the {@link Response} interface
 *            <p>
 *            This interface is designed to encapsulate the combination of a {@link Packet}
 *            representing the original incoming message and the corresponding response of type {@link Response}.
 * @see Packet
 * @see Message
 * @see Response
 */
public interface PacketResponse<M extends Message, R extends Response> {

    /**
     * Retrieves the original message packet that triggered the response.
     *
     * @return the original message packet of type {@link Packet}
     */
    @NotNull Packet<M> packet();

    /**
     * Retrieves the response associated with the original message.
     *
     * @return the response of type {@link Response}
     */
    @NotNull R response();

}