package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import com.ohalee.redisbridge.api.messaging.request.MessageHandler;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public class MessageRegistrationImpl implements MessageRegistration {

    private final String namespace;
    private final Class<? extends BaseMessage> messageClass;
    private final Class<? extends BaseResponse> responseClass;
    private final MessageHandler<?> handler;
    private final ResponseMessageHandler<?, ?> responseHandler;

    @Override
    public @NotNull String namespace() {
        return this.namespace;
    }

    @Override
    public @NotNull Class<? extends BaseMessage> messageClass() {
        return this.messageClass;
    }

    @Override
    public Class<? extends BaseResponse> responseClass() {
        return this.responseClass;
    }

    @Override
    public boolean expectsResponse() {
        return this.responseClass != null;
    }

    @Override
    public @Nullable MessageHandler<?> handler() {
        return this.handler;
    }

    @Override
    public @Nullable ResponseMessageHandler<?, ?> responseHandler() {
        return this.responseHandler;
    }

}