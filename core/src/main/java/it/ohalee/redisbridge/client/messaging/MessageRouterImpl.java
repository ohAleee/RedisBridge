package it.ohalee.redisbridge.client.messaging;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import it.ohalee.redisbridge.api.messaging.MessageRouter;
import it.ohalee.redisbridge.api.messaging.Sender;
import it.ohalee.redisbridge.api.messaging.request.Message;
import it.ohalee.redisbridge.api.messaging.request.BaseMessage;
import it.ohalee.redisbridge.api.messaging.MessageEntity;
import it.ohalee.redisbridge.api.messaging.response.MessageResponse;
import it.ohalee.redisbridge.api.messaging.response.BaseResponse;
import it.ohalee.redisbridge.api.messaging.response.ResponseDeserializer;
import it.ohalee.redisbridge.client.RedisBridgeClient;
import it.ohalee.redisbridge.client.messaging.request.MessageImpl;
import it.ohalee.redisbridge.client.messaging.response.MessageResponseImpl;
import it.ohalee.redisbridge.client.messaging.response.ResponseDeserializerImpl;
import it.ohalee.redisbridge.client.util.GsonProvider;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
public class MessageRouterImpl implements MessageRouter {

    private final RedisBridgeClient redisBridgeClient;

    private ResponseDeserializer responseDeserializer;
    private StatefulRedisPubSubConnection<String, String> connection;

    @Override
    public void load() {
        this.connection = this.redisBridgeClient.getRedis().pubSubConnection();

        this.responseDeserializer = new ResponseDeserializerImpl(this.redisBridgeClient, connection, connection.async());
        this.responseDeserializer.load();
    }

    @Override
    public void unload() {
        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
        if (this.responseDeserializer != null) {
            this.responseDeserializer.unload();
            this.responseDeserializer = null;
        }
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publish(@NotNull T message, @NotNull MessageEntity receiver) {
        MessageImpl<T> actionMessage = new MessageImpl<>(UUID.randomUUID(), Sender.from(this.redisBridgeClient.serverID(), this.redisBridgeClient.platformEntity()), message);

        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            return connection.async().publish(receiver.channel(), GsonProvider.normal().toJson(actionMessage))
                    .thenApply(ignored -> actionMessage);
        }
    }

    @Override
    public <M extends BaseMessage, R extends BaseResponse> void publishResponse(@NotNull MessageResponse<M, R> messageResponse, @NotNull MessageEntity receiver) {
        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            connection.async().publish(receiver.channel(), GsonProvider.normal().toJson(messageResponse));
        }
    }

    @Override
    public <M extends BaseMessage, R extends BaseResponse> void publishResponse(@NotNull Message<M> original, @NotNull R response, @NotNull MessageEntity receiver) {
        this.publishResponse(MessageResponseImpl.<M, R>builder().originalMessage(original).response(response).build(), receiver);
    }

    @Override
    public <M extends BaseMessage, R extends BaseResponse> CompletableFuture<MessageResponse<M, R>> waitResponse(@NotNull M message, @NotNull MessageEntity receiver) {
        return this.responseDeserializer.handle(this.publish(message, receiver));
    }

    @Override
    public <M extends BaseMessage, R extends BaseResponse> CompletableFuture<MessageResponse<M, R>> waitResponse(@NotNull M message, @NotNull MessageEntity receiver, @NotNull Class<R> responseClass) {
        return this.responseDeserializer.handle(this.publish(message, receiver));
    }

}
