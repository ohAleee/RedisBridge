package com.ohalee.redisbridge.api.messaging.request;

import com.google.gson.JsonObject;

/**
 * Interface for processing raw message data received from Redis into typed message objects.
 *
 * <p>This processor is responsible for deserializing and reconstructing message objects
 * from their serialized form as transmitted through Redis pub/sub channels.</p>
 *
 * @see Message
 * @see MessageRegistry
 */
public interface MessageDeserializer {

    void load();

    void unload();

    /**
     * Parses a full message from its JSON representation.
     *
     * @param jsonMessage The JSON object representing the full message
     * @param clazz       The class type of the message payload
     * @param <T>         The type of the message payload
     * @return The reconstructed full message object
     */
    <T extends BaseMessage> Message<T> parseMessage(JsonObject jsonMessage, Class<T> clazz);

    /**
     * Parses a request message from its JSON representation.
     *
     * @param jsonMessage The JSON object representing the request message
     * @param clazz       The class type of the message payload
     * @param <T>         The type of the message payload
     * @return The reconstructed request message object
     */
    <T extends BaseMessage> T parseRequest(JsonObject jsonMessage, Class<T> clazz);

}
