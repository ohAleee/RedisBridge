package it.ohalee.redisbridge.client.messaging.response;

import it.ohalee.redisbridge.api.messaging.request.Message;
import it.ohalee.redisbridge.api.messaging.request.BaseMessage;
import it.ohalee.redisbridge.api.messaging.response.MessageResponse;
import it.ohalee.redisbridge.api.messaging.response.BaseResponse;
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
