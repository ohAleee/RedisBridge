package com.ohalee.redisbridge.client.messaging.request;

import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Builder
public record PacketImpl<M extends Message>(UUID uniqueId, Sender sender, M message) implements Packet<M> {

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
