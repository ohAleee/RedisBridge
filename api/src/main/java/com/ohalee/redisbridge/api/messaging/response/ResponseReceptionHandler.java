package com.ohalee.redisbridge.api.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for processing incoming response messages from Redis.
 */
public interface ResponseReceptionHandler {

    void load();

    void unload();

    /**
     * Registers interest in a response for the given message packet.
     *
     * @param message the message packet
     * @param <T>     the message type
     * @param <C>     the response type
     * @return a future that will be completed with the response
     */
    <T extends Message, C extends Response> CompletableFuture<PacketResponse<T, C>> handle(@NotNull Packet<T> message);

    /**
     * Registers interest in a response once the packet is available asynchronously.
     *
     * @param message the message packet future
     * @param <T>     the message type
     * @param <C>     the response type
     * @return a future that will be completed with the response
     */
    <T extends Message, C extends Response> CompletableFuture<PacketResponse<T, C>> handle(CompletableFuture<Packet<T>> message);

    /**
     * Cancels and completes exceptionally any pending response future bound to the
     * unique id.
     *
     * @param uniqueId the unique id of the message
     * @param cause    the cause of the cancellation
     */
    void cancel(@NotNull UUID uniqueId, @NotNull Throwable cause);

}
