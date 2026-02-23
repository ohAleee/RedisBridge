package com.ohalee.redisbridge.client.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.messaging.response.Response;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

@Builder
public record PacketResponseImpl<M extends Message, R extends Response>(Packet<M> originalMessage, R response) implements PacketResponse<M, R> {

    @Override
    public @NotNull Packet<M> packet() {
        return this.originalMessage;
    }

    @Override
    public @NotNull R response() {
        return this.response;
    }
}
