package it.ohalee.redisbridge.api.messaging;

import it.ohalee.redisbridge.api.messaging.request.Message;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a message routing entity that defines the Redis channel pattern for message delivery.
 *
 * <p>This record encapsulates the channel information used by Redis pub/sub for routing messages
 * to specific receivers or groups of receivers.</p>
 *
 * <p><b>Factory Methods:</b></p>
 * <ul>
 *   <li>{@link #of(String)} - Creates an entity for targeting a specific server by its unique ID</li>
 *   <li>{@link #response(Sender)} - Creates an entity for sending responses back to the original sender</li>
 *   <li>{@link #response(String)} - Creates an entity for sending responses back to the original sender by server ID</li>
 * </ul>
 *
 * @see Message
 */
public interface MessageEntity {

    /**
     * The prefix used for all Redis channels. Can be customized by setting the system property
     * "redisbridge.channel.prefix" or environment variable "REDISBRIDGE_CHANNEL_PREFIX".
     * Defaults to "redisbridge" if not specified.
     */
    String PREFIX = System.getProperty("redisbridge.channel.prefix",
            System.getenv().getOrDefault("REDISBRIDGE_CHANNEL_PREFIX", "redisbridge"));

    MessageEntity BROADCAST = of("broadcast");

    /**
     * Creates a message entity for targeting a specific server.
     *
     * @param serverID the unique identifier of the target server
     * @return a message entity targeting the specified server
     */
    static @NotNull MessageEntity of(@NotNull String serverID) {
        return () -> PREFIX + ":target:" + serverID.toLowerCase();
    }

    /**
     * Creates a message entity for sending a response back to the original message sender.
     *
     * @param serverID the unique identifier of the server to respond to
     * @return a message entity targeting the specific sender's response channel
     */
    static @NotNull MessageEntity response(@NotNull String serverID) {
        return () -> PREFIX + ":response:" + serverID.toLowerCase();
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
     * Gets the Redis channel associated with this entity.
     *
     * @return The Redis channel name.
     */
    @NotNull
    String channel();

}
