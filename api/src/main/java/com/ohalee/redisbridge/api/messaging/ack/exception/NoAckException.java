package com.ohalee.redisbridge.api.messaging.ack.exception;

/**
 * Thrown when an expected acknowledgement (ACK) is not received within the configured timeout.
 */
public class NoAckException extends RuntimeException {
    public NoAckException() {
        super("No acknowledgement received within the configured timeout");
    }
}
