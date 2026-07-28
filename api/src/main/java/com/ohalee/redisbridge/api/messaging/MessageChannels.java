package com.ohalee.redisbridge.api.messaging;

import org.jetbrains.annotations.NotNull;

/**
 * The Redis channel namespace of a single project.
 *
 * <p>Every channel produced by this class is prefixed with {@link #prefix()}, so two projects
 * running with different prefixes never see each other's traffic, even inside the same JVM.
 * Obtain one with {@link #withPrefix(String)} and hand it to a client
 * (see {@code RedisBridgeClient.Builder#channelPrefix(String)} or by overriding
 * {@code RedisBridgeClient#channels()}):</p>
 *
 * <pre>{@code
 * MessageChannels channels = MessageChannels.withPrefix("my-project");
 * channels.of("server-1");      // my-project:target:server-1
 * channels.broadcast("chat");   // my-project:chat:broadcast
 * }</pre>
 *
 * <p>{@link #defaults()} returns the JVM-wide namespace, whose prefix comes from the
 * {@value #PREFIX_PROPERTY} system property, then the {@value #PREFIX_ENVIRONMENT_VARIABLE}
 * environment variable, and finally {@value #DEFAULT_PREFIX}. It is resolved once, when this
 * class is loaded; per-project prefixes are the supported way to override it at runtime.</p>
 *
 * <p><b>Note:</b> clients only subscribe to channels of their own namespace, so peers that
 * exchange messages must share the same prefix.</p>
 *
 * @param prefix the channel prefix, without a trailing {@code ':'}
 * @see MessageEntity
 */
public record MessageChannels(@NotNull String prefix) {

    /**
     * The prefix used when nothing else is configured.
     */
    public static final String DEFAULT_PREFIX = "redisbridge";

    /**
     * System property holding the JVM-wide default prefix.
     */
    public static final String PREFIX_PROPERTY = "redisbridge.channel.prefix";

    /**
     * Environment variable holding the JVM-wide default prefix.
     */
    public static final String PREFIX_ENVIRONMENT_VARIABLE = "REDISBRIDGE_CHANNEL_PREFIX";

    private static final MessageChannels DEFAULT = new MessageChannels(resolveDefaultPrefix());

    public MessageChannels {
        if (prefix == null) {
            throw new IllegalArgumentException("channel prefix must not be null");
        }

        prefix = prefix.trim();
        while (prefix.endsWith(":")) {
            prefix = prefix.substring(0, prefix.length() - 1).trim();
        }

        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("channel prefix must not be blank");
        }
    }

    /**
     * Creates a channel namespace for the given prefix.
     *
     * @param prefix the project prefix (e.g. "my-project"); a trailing {@code ':'} is optional
     * @return the channel namespace for that prefix
     * @throws IllegalArgumentException if the prefix is null or blank
     */
    public static @NotNull MessageChannels withPrefix(@NotNull String prefix) {
        return new MessageChannels(prefix);
    }

    /**
     * Returns the JVM-wide default channel namespace.
     *
     * @return the default channel namespace
     */
    public static @NotNull MessageChannels defaults() {
        return DEFAULT;
    }

    private static String resolveDefaultPrefix() {
        String property = System.getProperty(PREFIX_PROPERTY);
        if (property != null && !property.isBlank()) {
            return property;
        }

        String environment = System.getenv(PREFIX_ENVIRONMENT_VARIABLE);
        if (environment != null && !environment.isBlank()) {
            return environment;
        }

        return DEFAULT_PREFIX;
    }

    /**
     * Creates a message entity for broadcasting to all servers of this namespace.
     * The channel is formatted as {@code prefix + ":broadcast"}.
     *
     * @return a message entity for broadcasting to all servers
     */
    public @NotNull MessageEntity broadcast() {
        return channel(this.prefix + ":broadcast");
    }

    /**
     * Creates a message entity for a custom broadcast channel of this namespace.
     * The channel is formatted as {@code prefix + ":" + name + ":broadcast"}.
     *
     * @param name the unique name of the broadcast channel (e.g., "chat", "updates")
     * @return a message entity for the specified broadcast channel
     */
    public @NotNull MessageEntity broadcast(@NotNull String name) {
        return channel(this.prefix + ":" + name.toLowerCase() + ":broadcast");
    }

    /**
     * Creates a message entity targeting a specific server of this namespace.
     * The channel is formatted as {@code prefix + ":target:" + serverID}.
     *
     * @param serverID the unique identifier of the target server
     * @return a message entity targeting the specified server
     */
    public @NotNull MessageEntity of(@NotNull String serverID) {
        return channel(this.prefix + ":target:" + serverID.toLowerCase());
    }

    /**
     * Creates a message entity for sending a response back to a server of this namespace.
     * The channel is formatted as {@code prefix + ":response:" + serverID}.
     *
     * @param serverID the unique identifier of the server to respond to
     * @return a message entity targeting the specific sender's response channel
     */
    public @NotNull MessageEntity response(@NotNull String serverID) {
        return channel(this.prefix + ":response:" + serverID.toLowerCase());
    }

    /**
     * Creates a message entity for sending a response back to the original message sender.
     *
     * @param sender the original message sender
     * @return a message entity targeting the specific sender's response channel
     */
    public @NotNull MessageEntity response(@NotNull Sender sender) {
        return response(sender.id());
    }

    /**
     * Creates a message entity for sending an acknowledgement (ACK) back to a server of this namespace.
     * The channel is formatted as {@code prefix + ":ack:" + serverID}.
     *
     * @param serverID the unique identifier of the server to acknowledge
     * @return a message entity targeting the specific sender's ACK channel
     */
    public @NotNull MessageEntity ack(@NotNull String serverID) {
        return channel(this.prefix + ":ack:" + serverID.toLowerCase());
    }

    /**
     * Creates a message entity for sending an acknowledgement (ACK) back to the original message sender.
     *
     * @param sender the original message sender
     * @return a message entity targeting the specific sender's ACK channel
     */
    public @NotNull MessageEntity ack(@NotNull Sender sender) {
        return ack(sender.id());
    }

    private static MessageEntity channel(String channel) {
        return () -> channel;
    }
}
