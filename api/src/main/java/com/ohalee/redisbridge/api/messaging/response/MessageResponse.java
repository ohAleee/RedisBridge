package com.ohalee.redisbridge.api.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a response containing the original message and its associated response data
 * in a request-response pattern.
 *
 * @param <M> the type of the original message, which extends the {@link BaseMessage} interface
 * @param <R> the type of the response, which extends the {@link BaseResponse} interface
 * <p>
 * This interface is designed to encapsulate the combination of a {@link Message}
 * representing the original incoming message and the corresponding response of type {@link BaseResponse}.
 *
 * @see Message
 * @see BaseMessage
 * @see BaseResponse
 */
public interface MessageResponse<M extends BaseMessage, R extends BaseResponse> {

    /**
     * Retrieves the original full message associated with this response.
     *
     * @return the original full message of type {@link Message}
     */
    @NotNull Message<M> originalMessage();

    /**
     * Retrieves the response associated with the original message.
     *
     * @return the response of type {@link BaseResponse}
     */
    @NotNull R response();

}