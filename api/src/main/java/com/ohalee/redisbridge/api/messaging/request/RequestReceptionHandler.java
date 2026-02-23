package com.ohalee.redisbridge.api.messaging.request;

import com.google.gson.JsonObject;
import com.ohalee.redisbridge.api.messaging.MessageEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for processing incoming request messages from Redis.
 */
public interface RequestReceptionHandler {

    void load();

    void unload();

    /**
     * Subscribes to a broadcast channel.
     *
     * @param entity the broadcast entity to subscribe to
     */
    void subscribe(@NotNull MessageEntity entity);

    /**
     * Unsubscribes from a broadcast channel.
     *
     * @param entity the broadcast entity to unsubscribe from
     */
    void unsubscribe(@NotNull MessageEntity entity);

    /**
     * Parses a full message from its JSON representation.
     *
     * @param jsonMessage The JSON object representing the full message
     * @param clazz       The class type of the message payload
     * @param <T>         The type of the message payload
     * @return The reconstructed full message object
     */
    <T extends Message> Packet<T> parseMessage(JsonObject jsonMessage, Class<T> clazz);

    /**
     * Parses a request message from its JSON representation.
     *
     * @param jsonMessage The JSON object representing the request message
     * @param clazz       The class type of the message payload
     * @param <T>         The type of the message payload
     * @return The reconstructed request message object
     */
    <T extends Message> T parseRequest(JsonObject jsonMessage, Class<T> clazz);

}
