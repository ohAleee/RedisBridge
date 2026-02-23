package com.ohalee.redisbridge.api.messaging.request;

/**
 * Marker interface for all messages that can be transmitted through the Redis bridge system.
 *
 * <p>All message types must implement this interface to be processed by the message router.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @MessageName("user:login")
 * public record UserLoginMessage(String username, long timestamp) implements Packet {}
 * }</pre>
 *
 * @see Packet
 * @see MessageHandler
 */
public interface Message {

    /**
     * Whether this message requires an acknowledgement (ACK) from the receiver.
     *
     * <p>If enabled, the sender will wait for an ACK on the dedicated ACK channel.</p>
     *
     * @return {@code true} if acknowledgement is enabled, {@code false} otherwise
     */
    default boolean ackEnabled() {
        return false;
    }

}
