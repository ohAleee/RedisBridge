package com.ohalee.redisbridge.client.messaging.response;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import com.ohalee.redisbridge.api.messaging.response.MessageResponse;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Builder
@RequiredArgsConstructor
public class MessageResponseImpl<M extends BaseMessage, R extends BaseResponse> implements MessageResponse<M, R> {

    private final Message<M> originalMessage;
    private final R response;

    @Override
    public @NotNull Message<M> originalMessage() {
        return this.originalMessage;
    }

    @Override
    public @NotNull R response() {
        return this.response;
    }
}
