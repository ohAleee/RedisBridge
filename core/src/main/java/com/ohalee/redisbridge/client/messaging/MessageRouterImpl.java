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
import com.ohalee.redisbridge.client.messaging.ack.AckDeserializerImpl;
import com.ohalee.redisbridge.client.messaging.request.MessageImpl;
import com.ohalee.redisbridge.client.messaging.response.MessageResponseImpl;
import com.ohalee.redisbridge.client.messaging.response.ResponseDeserializerImpl;
import com.ohalee.redisbridge.client.util.GsonProvider;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.*;

public class MessageRouterImpl implements MessageRouter {

    private final RedisBridgeClient redisBridgeClient;
    private final Sender sender;

    private ResponseDeserializer responseDeserializer;
    private AckDeserializerImpl ackDeserializer;
    private StatefulRedisPubSubConnection<String, String> connection;

    private ScheduledExecutorService queueExecutor;
    private final ConcurrentLinkedQueue<QueuedMessage<?>> messageQueue = new ConcurrentLinkedQueue<>();
    private long queueIntervalMs = 100;

    public MessageRouterImpl(RedisBridgeClient redisBridgeClient) {
        this.redisBridgeClient = redisBridgeClient;
        this.connection = this.redisBridgeClient.getRedis().pubSubConnection();

        this.sender = Sender.from(this.redisBridgeClient.serverID(), this.redisBridgeClient.platformEntity());

        this.queueExecutor = Executors.newScheduledThreadPool(1, Thread.ofVirtual()
                .name("RedisBridge-QueuePublisher")
                .factory());
    }

    @Override
    public void load() {
        this.responseDeserializer = new ResponseDeserializerImpl(this.redisBridgeClient, connection, connection.async());
        this.responseDeserializer.load();

        this.ackDeserializer = new AckDeserializerImpl(this.redisBridgeClient, connection);
        this.ackDeserializer.load();

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

        if (this.ackDeserializer != null) {
            this.ackDeserializer.unload();
            this.ackDeserializer = null;
        }
    }

    private void processBatchPublish() {
        if (messageQueue.isEmpty()) return;

        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            var async = connection.async();

            QueuedMessage<?> queuedMessage;
            while ((queuedMessage = messageQueue.poll()) != null) {
                processQueueMessage(async, queuedMessage);
            }
        } catch (Exception e) {
            QueuedMessage<?> queuedMessage;
            while ((queuedMessage = messageQueue.poll()) != null) {
                queuedMessage.future.completeExceptionally(e);
            }
        }
    }

    private void processQueueMessage(RedisAsyncCommands<String, String> async, QueuedMessage<?> queuedMessage) {
        try {
            CompletableFuture<Message<?>> resultFuture = queuedMessage.message.ackRequested() ?
                    this.ackDeserializer.expectAck(queuedMessage.message.uniqueId())
                            .thenApply(a -> queuedMessage.message) :
                    CompletableFuture.completedFuture(queuedMessage.message);

            async.publish(queuedMessage.receiver.channel(), GsonProvider.normal().toJson(queuedMessage.message))
                    .thenAccept(ignored -> {
                        resultFuture.thenAccept(msg -> queuedMessage.future.complete((Message) msg))
                                .exceptionally(throwable -> {
                                    queuedMessage.future.completeExceptionally(throwable);
                                    return null;
                                });
                    })
                    .exceptionally(throwable -> {
                        queuedMessage.future.completeExceptionally(throwable);
                        return null;
                    });
        } catch (Exception e) {
            queuedMessage.future.completeExceptionally(e);
        }
    }


    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publish(@NotNull T message, @NotNull MessageEntity receiver) {
        return this.publishImmediate(message, receiver);
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publishImmediate(@NotNull T message, @NotNull MessageEntity receiver) {
        MessageImpl<T> actionMessage = new MessageImpl<>(UUID.randomUUID(), this.sender, message);

        CompletableFuture<Message<T>> resultFuture = actionMessage.ackRequested() ?
                this.ackDeserializer.expectAck(actionMessage.uniqueId())
                        .thenApply(a -> actionMessage) :
                CompletableFuture.completedFuture(actionMessage);

        String jsonMessage = GsonProvider.normal().toJson(actionMessage);
        try (var connection = this.redisBridgeClient.getRedis().connection()) {
            connection.async().publish(receiver.channel(), jsonMessage);
        }

        return resultFuture;
    }

    @Override
    public <T extends BaseMessage> CompletionStage<Message<T>> publishQueued(@NotNull T message, @NotNull MessageEntity receiver) {
        MessageImpl<T> actionMessage = new MessageImpl<>(UUID.randomUUID(), this.sender, message);
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

    private record QueuedMessage<T extends BaseMessage>(Message<T> message, MessageEntity receiver,
                                                        CompletableFuture<Message<T>> future) {
    }
}
