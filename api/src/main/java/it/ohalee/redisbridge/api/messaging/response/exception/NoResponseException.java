package it.ohalee.redisbridge.api.messaging.response.exception;

/**
 * Exception thrown when a response is expected but not received within the timeout period.
 *
 * <p>This exception is typically thrown when using {@link it.ohalee.redisbridge.api.messaging.MessageRouter#waitResponse}
 * methods and the remote handler fails to respond or takes too long to respond.</p>
 *
 * <p><b>Common Causes:</b></p>
 * <ul>
 *   <li>Remote service is down or unresponsive</li>
 *   <li>Network connectivity issues</li>
 *   <li>Handler processing takes longer than the configured timeout</li>
 *   <li>No handler registered for the message type on the receiving end</li>
 * </ul>
 */
public class NoResponseException extends RuntimeException {

    /**
     * Constructs a new NoResponseException with the specified detail message.
     *
     * @param message the detail message explaining why no response was received
     */
    public NoResponseException(String message) {
        super(message);
    }

    /**
     * Constructs a new NoResponseException with a default message.
     */
    public NoResponseException() {
        super("No response received within the expected timeout period.");
    }
}