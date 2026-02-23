package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageHandler;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Builder
@RequiredArgsConstructor
public class MessageRegistrationImpl implements MessageRegistration {

    private final String namespace;
    private final Class<? extends Message> messageClass;
    private final Class<? extends Response> responseClass;
    private final MessageHandler<?> handler;
    private final ResponseMessageHandler<?, ?> responseHandler;

    @Override
    public @NotNull String namespace() {
        return this.namespace;
    }

    @Override
    public @NotNull Class<? extends Message> messageClass() {
        return this.messageClass;
    }

    @Override
    public Class<? extends Response> responseClass() {
        return this.responseClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <M extends Message> MessageHandler<M> handler() {
        return (MessageHandler<M>) this.handler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <M extends Message, R extends Response> ResponseMessageHandler<M, R> responseHandler() {
        return (ResponseMessageHandler<M, R>) this.responseHandler;
    }

}