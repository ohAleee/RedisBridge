package com.ohalee.redisbridge.api.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Interface for processing raw response data received from Redis into typed response objects.
 *
 * <p>This processor is responsible for deserializing and reconstructing response objects
 * from their serialized form as transmitted through Redis pub/sub channels.</p>
 *
 * @see MessageResponse
 */
public interface ResponseDeserializer {

    void load();

    void unload();

    /**
     * Handles an incoming message and produces a response.
     *
     * @param message A CompletionStage that will provide the FullMessage to be handled
     * @param <T>     The type of the incoming message
     * @param <C>     The type of the response message
     * @return A CompletableFuture that will complete with the FullMessageResponse
     */
    <T extends BaseMessage, C extends BaseResponse> CompletableFuture<MessageResponse<T, C>> handle(CompletionStage<Message<T>> message);

}
