package com.ohalee.redisbridge.api.messaging.request;

/**
 * Marker interface for all messages that can be transmitted through the Redis bridge system.
 *
 * <p>All message types must implement this interface to be processed by the message router.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public record UserLoginMessage(String username, long timestamp) implements Message {}
 * }</pre>
 *
 * @see Message
 * @see MessageHandler
 */
public interface BaseMessage {

    String namespace();

}
