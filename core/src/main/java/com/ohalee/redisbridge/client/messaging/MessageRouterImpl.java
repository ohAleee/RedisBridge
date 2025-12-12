package com.ohalee.redisbridge.client.messaging;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.MessageRouter;
import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.api.messaging.response.MessageResponse;
import com.ohalee.redisbridge.api.messaging.response.ResponseDeserializer;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.request.MessageImpl;
import com.ohalee.redisbridge.client.messaging.response.MessageResponseImpl;
import com.ohalee.redisbridge.client.messaging.response.ResponseDeserializerImpl;
import com.ohalee.redisbridge.client.util.GsonProvider;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.*;

@RequiredArgsConstructor
public class MessageRouterImpl implements MessageRouter {

    private final RedisBridgeClient redisBridgeClient;

    private ResponseDeserializer responseDeserializer;
    private StatefulRedisPubSubConnection<String, String> connection;

    private ScheduledExecutorService queueExecutor;
    private ConcurrentLinkedQueue<QueuedMessage<?>> messageQueue;
    private long queueIntervalMs = 100;

    private record QueuedMessage<T extends BaseMessage>(Message<T> message, MessageEntity receiver,
                                                        CompletableFuture future) {
    }

    @Override
    public void load() {
        this.connection = this.redisBridgeClient.getRedis().pubSubConnection();

        this.responseDeserializer = new ResponseDeserializerImpl(this.redisBridgeClient, connection, connection.async());
        this.responseDeserializer.load();

        this.messageQueue = new ConcurrentLinkedQueue<>();
        this.queueExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RedisBridge-QueuePublisher");
            thread.setDaemon(true);
            return thread;
        });

        this.queueExecutor.scheduleAtFixedRate(this::processBatchPublish, queueIntervalMs, queueIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        if (this.queueExecutor != null) {
            this.queueExecutor.shutdown();
            try {
                processBatchPublish();

                if (!this.queueExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.queueExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.queueExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            this.queueExecutor = null;
        }

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
        if (this.responseDeserializer != null) {
            this.responseDeserializer.unload();
            this.responseDeserializer = null;
        }
    }

    private void processBatchPublish() {
        if (messageQueue.isEmpty()) return;

        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            var async = connection.async();

            QueuedMessage<?> queuedMessage;
            while ((queuedMessage = messageQueue.poll()) != null) {
                QueuedMessage<?> finalQueuedMessage = queuedMessage;
                try {
                    async.publish(queuedMessage.receiver.channel(), GsonProvider.normal().toJson(queuedMessage.message))
                            .thenAccept(ignored -> finalQueuedMessage.future.complete(finalQueuedMessage.message))
                            .exceptionally(throwable -> {
                                finalQueuedMessage.future.completeExceptionally(throwable);
                                return null;
                            });
                } catch (Exception e) {
                    queuedMessage.future.completeExceptionally(e);
                }
            }
        } catch (Exception e) {
            QueuedMessage<?> queuedMessage;
            while ((queuedMessage = messageQueue.poll()) != null) {
                queuedMessage.future.completeExceptionally(e);
            }
        }
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publish(@NotNull T message, @NotNull MessageEntity receiver) {
        return this.publishImmediate(message, receiver);
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publishImmediate(@NotNull T message, @NotNull MessageEntity receiver) {
        MessageImpl<T> actionMessage = new MessageImpl<>(UUID.randomUUID(), Sender.from(this.redisBridgeClient.serverID(), this.redisBridgeClient.platformEntity()), message);

        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            return connection.async().publish(receiver.channel(), GsonProvider.normal().toJson(actionMessage))
                    .thenApply(ignored -> actionMessage);
        }
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publishQueued(@NotNull T message, @NotNull MessageEntity receiver) {
        MessageImpl<T> actionMessage = new MessageImpl<>(UUID.randomUUID(), Sender.from(this.redisBridgeClient.serverID(), this.redisBridgeClient.platformEntity()), message);
        CompletableFuture<Message<T>> future = new CompletableFuture<>();

        messageQueue.offer(new QueuedMessage<>(actionMessage, receiver, future));

        return future;
    }

    @Override
    public void configureQueuedPublishing(long intervalMs) {
        this.queueIntervalMs = intervalMs;
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
