package com.ohalee.redisbridge.api.messaging.request;

import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

/**
 * Registry for managing message handler registrations and subscriptions.
 *
 * <p>This registry maintains mappings between message types and their handlers,
 * allowing the message router to dispatch incoming messages to the appropriate
 * processing logic.</p>
 *
 * <p><b>Handler Registration:</b></p>
 * <ul>
 *   <li>Handlers without responses: {@link #register(String, Class)}</li>
 *   <li>Handlers that produce responses: {@link #register(String, Class, Class)}</li>
 * </ul>
 *
 * @see MessageRegistration
 * @see MessageHandler
 * @see VoidMessageHandler
 */
public interface MessageRegistry {

    /**
     * Internal cache for namespace identifiers.
     * Uses ClassValue to avoid reflection overhead on subsequent calls.
     */
    final class NamespaceCache {
        private static final ClassValue<String> CACHE = new ClassValue<>() {
            @Override
            protected @NotNull String computeValue(Class<?> type) {
                if (type.isAnnotationPresent(MessageName.class)) {
                    return type.getAnnotation(MessageName.class).value();
                }
                return type.getSimpleName();
            }
        };
    }

    /**
     * Utility method to get the namespace of a message class.
     *
     * @param messageClass the message class
     * @return the namespace
     */
    static String getNamespace(@NotNull Class<? extends Message> messageClass) {
        return NamespaceCache.CACHE.get(messageClass);
    }

    /**
     * Utility method to get the namespace of a message instance.
     *
     * @param message the message instance
     * @return the namespace
     */
    static String getNamespace(@NotNull Message message) {
        return getNamespace(message.getClass());
    }

    /**
     * Get a registration builder for a message without response.
     * The namespace is automatically derived from the message class.
     *
     * @param messageClass the message class
     * @param <M>          the message type
     * @return a registration builder
     */
    default @NotNull <M extends Message> RegistrationBuilder<M> register(@NotNull Class<M> messageClass) {
        return register(getNamespace(messageClass), messageClass);
    }

    /**
     * Get a registration builder for a message that doesn't expect a response
     *
     * @param namespace    the namespace identifier
     * @param messageClass the message class
     * @return a registration builder
     */
    @NotNull
    <M extends Message> RegistrationBuilder<M> register(@NotNull String namespace, @NotNull Class<M> messageClass);

    /**
     * Get a registration builder for a message that expects a response.
     * The namespace is automatically derived from the message class.
     *
     * @param messageClass  the message class
     * @param responseClass the response class
     * @param <M>           the message type
     * @param <R>           the response type
     * @return a registration builder
     */
    default @NotNull <M extends Message, R extends Response> RegistrationBuilderWithResponse<M, R> register(@NotNull Class<M> messageClass, @NotNull Class<R> responseClass) {
        return register(getNamespace(messageClass), messageClass, responseClass);
    }

    /**
     * Get a registration builder for a message that expects a response
     *
     * @param namespace     the namespace identifier
     * @param messageClass  the message class
     * @param responseClass the response class
     * @return a registration builder
     */
    @NotNull
    <M extends Message, R extends Response> RegistrationBuilderWithResponse<M, R> register(@NotNull String namespace, @NotNull Class<M> messageClass, @NotNull Class<R> responseClass);

    /**
     * Get the registration for a given namespace
     *
     * @param namespace the namespace identifier
     * @return the registration, or null if not found
     */
    @Nullable
    MessageRegistration getRegistration(@NotNull String namespace);

    /**
     * Get the registration for a given message class
     *
     * @param messageClass the message class
     * @return the registration, or null if not found
     */
    @Nullable
    default MessageRegistration getRegistration(@NotNull Class<? extends Message> messageClass) {
        return getRegistration(getNamespace(messageClass));
    }

    /**
     * Check if a namespace is registered
     *
     * @param namespace the namespace identifier
     * @return true if registered
     */
    boolean isRegistered(@NotNull String namespace);

    /**
     * Check if a message class is registered
     *
     * @param messageClass the message class
     * @return true if registered
     */
    default boolean isRegistered(@NotNull Class<? extends Message> messageClass) {
        return isRegistered(getNamespace(messageClass));
    }

    /**
     * Builder for message registration without response
     */
    interface RegistrationBuilder<M extends Message> {
        /**
         * Set the handler for when this message is received
         *
         * @param handler the handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler);

        /**
         * Set the handler for when this message is received, to be executed on a specific executor
         *
         * @param handler  the handler
         * @param executor the custom executor to run the handler on
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler, @NotNull Executor executor);

        /**
         * Complete the registration
         */
        void build();
    }

    /**
     * Builder for message registration with response
     */
    interface RegistrationBuilderWithResponse<M extends Message, R extends Response> {
        /**
         * Set the handler for when this message is received
         *
         * @param handler the handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onReceive(@NotNull MessageHandler<M> handler);

        /**
         * Set the handler for when this message is received, to be executed on a specific executor
         *
         * @param handler  the handler
         * @param executor the custom executor to run the handler on
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onReceive(@NotNull MessageHandler<M> handler, @NotNull Executor executor);

        /**
         * Set the handler for when a response to this message is received
         *
         * @param handler the response handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> handler);

        /**
         * Set the handler for when a response to this message is received, to be executed on a specific executor
         *
         * @param handler  the response handler
         * @param executor the custom executor to run the handler on
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> handler, @NotNull Executor executor);

        /**
         * Complete the registration
         */
        void build();
    }
}