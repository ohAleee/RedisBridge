package it.ohalee.redisbridge.api.messaging.response;

/**
 * Marker interface for all response objects that can be sent back through the Redis bridge system.
 *
 * <p>Response objects are sent back to message senders when using the request-response pattern.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public record LoginResponse(boolean authenticated, String token) implements MessageResponse {}
 * }</pre>
 *
 * @see MessageResponse
 * @see ResponseMessageHandler
 */
public interface BaseResponse {
}
