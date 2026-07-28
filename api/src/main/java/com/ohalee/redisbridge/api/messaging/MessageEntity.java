package com.ohalee.redisbridge.api.messaging;

import com.ohalee.redisbridge.api.messaging.request.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a message routing entity that defines the Redis channel pattern for message delivery.
 *
 * <p>This record encapsulates the channel information used by Redis pub/sub for routing messages
 * to specific receivers or groups of receivers.</p>
 *
 * <p>The static factory methods below build channels inside the JVM-wide default namespace
 * ({@link MessageChannels#defaults()}). Projects that need their own channel prefix should use
 * their client's namespace instead — {@code client.channels().broadcast("updates")} — or a
 * namespace built with {@link MessageChannels#withPrefix(String)}.</p>
 *
 * <p><b>Factory Methods:</b></p>
 * <ul>
 *   <li>{@link #broadcast()} - Creates an entity for broadcasting to all servers</li>
 *   <li>{@link #broadcast(String)} - Creates an entity for a custom broadcast channel</li>
 *   <li>{@link #ack(Sender)} - Creates an entity for sending acknowledgements back to the original sender</li>
 *   <li>{@link #ack(String)} - Creates an entity for sending acknowledgements back to the original sender by server ID</li>
 *   <li>{@link #of(String)} - Creates an entity for targeting a specific server by its unique ID</li>
 *   <li>{@link #response(Sender)} - Creates an entity for sending responses back to the original sender</li>
 *   <li>{@link #response(String)} - Creates an entity for sending responses back to the original sender by server ID</li>
 * </ul>
 *
 * @see Packet
 */
public interface MessageEntity {

    /**
     * The JVM-wide default channel prefix, resolved from the system property
     * "redisbridge.channel.prefix", then the environment variable "REDISBRIDGE_CHANNEL_PREFIX",
     * and finally "redisbridge".
     *
     * <p>This is only the default: each client can use its own prefix, see
     * {@link MessageChannels}.</p>
     */
    String PREFIX = MessageChannels.defaults().prefix();

    /**
     * Creates a message entity for broadcasting to all servers.
     * The channel will be formatted as {@code PREFIX + ":broadcast"}.
     *
     * @return a message entity for broadcasting to all servers
     */
    static @NotNull MessageEntity broadcast() {
        return MessageChannels.defaults().broadcast();
    }

    /**
     * Creates a message entity for a custom broadcast channel.
     * The channel will be formatted as {@code PREFIX + ":" + name + ":broadcast"}.
     *
     * @param name the unique name of the broadcast channel (e.g., "chat", "updates")
     * @return a message entity for the specified broadcast channel
     */
    static @NotNull MessageEntity broadcast(@NotNull String name) {
        return MessageChannels.defaults().broadcast(name);
    }

    /**
     * Creates a message entity for targeting a specific server.
     * The channel will be formatted as {@code PREFIX + ":target:" + serverID}.
     *
     * @param serverID the unique identifier of the target server
     * @return a message entity targeting the specified server
     */
    static @NotNull MessageEntity of(@NotNull String serverID) {
        return MessageChannels.defaults().of(serverID);
    }

    /**
     * Creates a message entity for sending a response back to the original message sender.
     * The channel will be formatted as {@code PREFIX + ":response:" + serverID}.
     *
     * @param serverID the unique identifier of the server to respond to
     * @return a message entity targeting the specific sender's response channel
     */
    static @NotNull MessageEntity response(@NotNull String serverID) {
        return MessageChannels.defaults().response(serverID);
    }

    /**
     * Creates a message entity for sending a response back to the original message sender.
     *
     * @param sender the original message sender
     * @return a message entity targeting the specific sender's response channel
     */
    static @NotNull MessageEntity response(@NotNull Sender sender) {
        return response(sender.id());
    }

    /**
     * Creates a message entity for sending an acknowledgement (ACK) back to the original message sender.
     * The channel will be formatted as {@code PREFIX + ":ack:" + serverID}.
     *
     * @param serverID the unique identifier of the server to acknowledge
     * @return a message entity targeting the specific sender's ACK channel
     */
    static @NotNull MessageEntity ack(@NotNull String serverID) {
        return MessageChannels.defaults().ack(serverID);
    }

    /**
     * Creates a message entity for sending an acknowledgement (ACK) back to the original message sender.
     *
     * @param sender the original message sender
     * @return a message entity targeting the specific sender's ACK channel
     */
    static @NotNull MessageEntity ack(@NotNull Sender sender) {
        return ack(sender.id());
    }

    /**
     * Gets the Redis channel associated with this entity.
     *
     * @return The Redis channel name.
     */
    @NotNull
    String channel();

}
