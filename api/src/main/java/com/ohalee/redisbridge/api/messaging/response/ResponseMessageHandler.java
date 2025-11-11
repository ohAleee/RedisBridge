package com.ohalee.redisbridge.api.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling incoming responses to previously sent messages.
 *
 * <p>This handler is used in the request-response pattern to process responses
 * asynchronously when they arrive from the message receiver.</p>
 *
 * @param <M> the type of the original message
 * @param <R> the type of the response
 *
 * @see MessageResponse
 */
@FunctionalInterface
public interface ResponseMessageHandler<M extends BaseMessage, R extends BaseResponse> {

    /**
     * Handles the incoming response to a previously sent message.
     *
     * @param response the complete response package including the original message context
     */
    void handleResponse(@NotNull MessageResponse<M, R> response);

}
