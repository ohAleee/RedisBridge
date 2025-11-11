package com.ohalee.redisbridge.api.messaging.request;

import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a registered message handler configuration in the message registry.
 *
 * <p>This interface associates a message type with its handler and indicates whether
 * the handler produces a response. It is used internally by the message router
 * to dispatch incoming messages to the appropriate handlers.</p>
 *
 * @see MessageRegistry
 * @see MessageHandler
 * @see VoidMessageHandler
 */
public interface MessageRegistration {

    /**
     * Get the namespace identifier for this message
     *
     * @return the namespace
     */
    @NotNull
    String namespace();

    /**
     * Get the message class
     *
     * @return the message class
     */
    @NotNull
    Class<? extends BaseMessage> messageClass();

    /**
     * Get the response class if this message expects a response
     *
     * @return the response class, or null if no response is expected
     */
    Class<? extends BaseResponse> responseClass();

    /**
     * Check if this message expects a response
     *
     * @return true if a response is expected
     */
    boolean expectsResponse();


    /**
     * Get the handler for when the message is received
     *
     * @return the message handler
     */
    @Nullable
    MessageHandler<?> handler();

    /**
     * Get the handler for when the response is received
     * Only applicable when expectsResponse() returns true
     *
     * @return the response handler, or null if no response is expected
     */
    @Nullable
    ResponseMessageHandler<?, ?> responseHandler();

}
