package com.ohalee.redisbridge.api.messaging;

/**
 * Represents an entity that can send messages through the Redis bridge system.
 * This interface serves as a marker to identify message senders and provides
 * a unique identifier for tracking message origins.
 *
 * <p>Implementations should ensure that the ID returned by {@link #id()} is unique
 * across all senders in the distributed system.</p>
 */
public interface Sender {

    /**
     * Creates a new Sender instance with the specified registration ID and message entity.
     *
     * @param id the unique identifier for the sender
     * @param entity         the message entity associated with the sender
     * @return a new Sender instance
     */
    static Sender from(String id, MessageEntity entity) {
        return new Sender() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MessageEntity entity() {
                return entity;
            }
        };
    }

    /**
     * Returns the unique identifier of this sender.
     *
     * @return the sender's unique ID
     */
    String id();

    /**
     * Returns the message entity associated with this sender.
     *
     * @return the sender's message entity
     */
    MessageEntity entity();

}
