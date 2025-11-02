
package it.ohalee.redisbridge.api.messaging.request;

import it.ohalee.redisbridge.api.messaging.response.BaseResponse;
import it.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Get a registration builder for a message that doesn't expect a response
     *
     * @param namespace the namespace identifier
     * @param messageClass the message class
     * @return a registration builder
     */
    @NotNull
    <M extends BaseMessage> RegistrationBuilder<M> register(@NotNull String namespace, @NotNull Class<M> messageClass);

    /**
     * Get a registration builder for a message that expects a response
     *
     * @param namespace the namespace identifier
     * @param messageClass the message class
     * @param responseClass the response class
     * @return a registration builder
     */
    @NotNull
    <M extends BaseMessage, R extends BaseResponse> RegistrationBuilderWithResponse<M, R> register(@NotNull String namespace, @NotNull Class<M> messageClass, @NotNull Class<R> responseClass);

    /**
     * Get the registration for a given namespace
     *
     * @param namespace the namespace identifier
     * @return the registration, or null if not found
     */
    @Nullable
    MessageRegistration getRegistration(@NotNull String namespace);

    /**
     * Check if a namespace is registered
     *
     * @param namespace the namespace identifier
     * @return true if registered
     */
    boolean isRegistered(@NotNull String namespace);

    /**
     * Builder for message registration without response
     */
    interface RegistrationBuilder<M extends BaseMessage> {
        /**
         * Set the handler for when this message is received
         *
         * @param handler the handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler);

        /**
         * Complete the registration
         */
        void build();
    }

    /**
     * Builder for message registration with response
     */
    interface RegistrationBuilderWithResponse<M extends BaseMessage, R extends BaseResponse> {
        /**
         * Set the handler for when this message is received
         *
         * @param handler the handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onReceive(@NotNull MessageHandler<M> handler);

        /**
         * Set the handler for when a response to this message is received
         *
         * @param handler the response handler
         * @return this builder for chaining
         */
        @NotNull
        RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> handler);

        /**
         * Complete the registration
         */
        void build();
    }

}