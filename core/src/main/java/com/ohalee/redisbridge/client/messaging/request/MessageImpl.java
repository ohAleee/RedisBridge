package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Builder
@RequiredArgsConstructor
public class MessageImpl<M extends BaseMessage> implements Message<M> {

    private final UUID uniqueId;
    private final Sender sender;
    private final M message;

    @Override
    public @NotNull UUID uniqueId() {
        return this.uniqueId;
    }

    @Override
    public @NotNull Sender sender() {
        return this.sender;
    }

    @Override
    public @NotNull M message() {
        return this.message;
    }

    @Override
    public boolean ackRequested() {
        return this.message.ackEnabled();
    }
}
