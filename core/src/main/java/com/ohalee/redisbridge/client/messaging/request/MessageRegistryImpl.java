
package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.request.*;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MessageRegistryImpl implements MessageRegistry {

    private final Map<String, MessageRegistration> registrations = new HashMap<>();

    @Override
    public @NotNull <M extends BaseMessage> RegistrationBuilder<M> register(@NotNull String namespace, @NotNull Class<M> messageClass) {
        return new PlatformRegistrationBuilder<>(namespace, messageClass);
    }

    @Override
    public @NotNull <M extends BaseMessage, R extends BaseResponse> RegistrationBuilderWithResponse<M, R> register(
            @NotNull String namespace,
            @NotNull Class<M> messageClass,
            @NotNull Class<R> responseClass) {
        return new PlatformRegistrationBuilderWithResponse<>(namespace, messageClass, responseClass);
    }

    @Override
    public @Nullable MessageRegistration getRegistration(@NotNull String namespace) {
        return registrations.get(namespace);
    }

    @Override
    public boolean isRegistered(@NotNull String namespace) {
        return registrations.containsKey(namespace);
    }

    /**
     * Builder implementation for messages without response
     */
    private class PlatformRegistrationBuilder<M extends BaseMessage> implements RegistrationBuilder<M> {
        private final String namespace;
        private final Class<M> messageClass;
        private VoidMessageHandler<M> handler;

        public PlatformRegistrationBuilder(String namespace, Class<M> messageClass) {
            this.namespace = namespace;
            this.messageClass = messageClass;
        }

        @Override
        public @NotNull RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler) {
            this.handler = handler;
            return this;
        }

        @Override
        public void build() {
            MessageHandler<M> wrappedHandler = message -> {
                if (handler != null) {
                    handler.handle(message);
                }
            };

            MessageRegistrationImpl registration = new MessageRegistrationImpl(
                    namespace,
                    messageClass,
                    null, // no response class
                    wrappedHandler,
                    null // no response handler
            );
            registrations.put(namespace, registration);
        }
    }

    /**
     * Builder implementation for messages with response
     */
    private class PlatformRegistrationBuilderWithResponse<M extends BaseMessage, R extends BaseResponse>
            implements RegistrationBuilderWithResponse<M, R> {

        private final String namespace;
        private final Class<M> messageClass;
        private final Class<R> responseClass;
        private MessageHandler<M> handler;
        private ResponseMessageHandler<M, R> responseHandler;

        public PlatformRegistrationBuilderWithResponse(String namespace, Class<M> messageClass, Class<R> responseClass) {
            this.namespace = namespace;
            this.messageClass = messageClass;
            this.responseClass = responseClass;
        }

        @Override
        public @NotNull RegistrationBuilderWithResponse<M, R> onReceive(@NotNull MessageHandler<M> handler) {
            this.handler = handler;
            return this;
        }

        @Override
        public @NotNull RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> responseHandler) {
            this.responseHandler = responseHandler;
            return this;
        }

        @Override
        public void build() {
            MessageRegistrationImpl registration = new MessageRegistrationImpl(
                    namespace,
                    messageClass,
                    responseClass,
                    handler,
                    responseHandler
            );
            registrations.put(namespace, registration);
        }
    }

}