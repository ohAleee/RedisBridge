package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.request.*;
import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class MessageRegistryImpl implements MessageRegistry {

    private final Map<String, MessageRegistration> registrations = new ConcurrentHashMap<>();

    @Override
    public @NotNull <M extends Message> RegistrationBuilder<M> register(@NotNull String namespace, @NotNull Class<M> messageClass) {
        return new PlatformRegistrationBuilder<>(namespace, messageClass);
    }

    @Override
    public @NotNull <M extends Message, R extends Response> RegistrationBuilderWithResponse<M, R> register(
            @NotNull String namespace,
            @NotNull Class<M> messageClass,
            @NotNull Class<R> responseClass) {
        return new PlatformRegistrationBuilderWithResponse<>(namespace, messageClass, responseClass);
    }

    @Override
    public @Nullable MessageRegistration getRegistration(@NotNull String namespace) {
        return this.registrations.get(namespace);
    }

    @Override
    public boolean isRegistered(@NotNull String namespace) {
        return this.registrations.containsKey(namespace);
    }

    /**
     * Builder implementation for messages without response
     */
    private class PlatformRegistrationBuilder<M extends Message> implements RegistrationBuilder<M> {
        private final String namespace;
        private final Class<M> messageClass;
        private VoidMessageHandler<M> handler;

        public PlatformRegistrationBuilder(String namespace, Class<M> messageClass) {
            this.namespace = namespace;
            this.messageClass = messageClass;
        }

        @Override
        public @NotNull RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler) {
            if (this.handler != null) {
                throw new IllegalStateException("Handler already set for this message");
            }
            this.handler = handler;
            return this;
        }

        @Override
        public @NotNull RegistrationBuilder<M> onReceive(@NotNull VoidMessageHandler<M> handler, @NotNull Executor executor) {
            if (this.handler != null) {
                throw new IllegalStateException("Handler already set for this message");
            }
            this.handler = message -> executor.execute(() -> handler.handle(message));
            return this;
        }

        @Override
        public void build() {
            MessageHandler<M> wrappedHandler = message -> {
                if (this.handler != null) {
                    this.handler.handle(message);
                }
            };

            MessageRegistrationImpl registration = MessageRegistrationImpl.builder()
                    .namespace(this.namespace)
                    .messageClass(this.messageClass)
                    .handler(wrappedHandler)
                    .build();

            if (registrations.putIfAbsent(this.namespace, registration) != null) {
                throw new IllegalStateException("A registration for namespace '" + this.namespace + "' already exists.");
            }
        }
    }

    /**
     * Builder implementation for messages with response
     */
    private class PlatformRegistrationBuilderWithResponse<M extends Message, R extends Response> implements RegistrationBuilderWithResponse<M, R> {
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
            if (this.handler != null) {
                throw new IllegalStateException("Handler already set for this message");
            }
            this.handler = handler;
            return this;
        }

        @Override
        public @NotNull RegistrationBuilderWithResponse<M, R> onReceive(@NotNull MessageHandler<M> handler, @NotNull Executor executor) {
            if (this.handler != null) {
                throw new IllegalStateException("Handler already set for this message");
            }
            this.handler = message -> executor.execute(() -> handler.handle(message));
            return this;
        }

        @Override
        public @NotNull RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> responseHandler) {
            if (this.responseHandler != null) {
                throw new IllegalStateException("Response handler already set for this message");
            }
            this.responseHandler = responseHandler;
            return this;
        }

        @Override
        public @NotNull RegistrationBuilderWithResponse<M, R> onResponse(@NotNull ResponseMessageHandler<M, R> responseHandler, @NotNull Executor executor) {
            if (this.responseHandler != null) {
                throw new IllegalStateException("Response handler already set for this message");
            }
            this.responseHandler = (packet) -> executor.execute(() -> responseHandler.handleResponse(packet));
            return this;
        }

        @Override
        public void build() {
            MessageRegistrationImpl registration = MessageRegistrationImpl.builder()
                    .namespace(this.namespace)
                    .messageClass(this.messageClass)
                    .responseClass(this.responseClass)
                    .handler(this.handler)
                    .responseHandler(this.responseHandler)
                    .build();

            if (registrations.putIfAbsent(this.namespace, registration) != null) {
                throw new IllegalStateException("A registration for namespace '" + this.namespace + "' already exists.");
            }
        }
    }

}