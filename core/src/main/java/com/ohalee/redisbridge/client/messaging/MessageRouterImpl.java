package com.ohalee.redisbridge.client.messaging;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.MessageRouter;
import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.messaging.interceptor.MessageInterceptor;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.api.messaging.response.ResponseReceptionHandler;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.ack.AckDeserializerImpl;
import com.ohalee.redisbridge.client.messaging.request.PacketImpl;
import com.ohalee.redisbridge.client.messaging.response.PacketResponseImpl;
import com.ohalee.redisbridge.client.messaging.response.ResponseReceptionHandlerImpl;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class MessageRouterImpl implements MessageRouter {

    private final RedisBridgeClient redisBridgeClient;
    private final RedisMessagingService messagingService;
    private final Sender sender;
    private final Settings settings;
    private final ConcurrentLinkedQueue<QueuedMessage<?>> messageQueue = new ConcurrentLinkedQueue<>();
    private ResponseReceptionHandler responseReceptionHandler;
    private AckDeserializerImpl ackDeserializer;
    private StatefulRedisPubSubConnection<String, String> connection;
    private @Nullable ScheduledExecutorService queueExecutor;

    public MessageRouterImpl(RedisBridgeClient client, Settings settings) {
        this.redisBridgeClient = client;
        this.messagingService = client.getMessagingService();
        this.settings = settings;
        this.connection = this.redisBridgeClient.getRedis().pubSubConnection();
        this.sender = Sender.from(this.redisBridgeClient.clientId(), this.redisBridgeClient.platformEntity());

        if (settings.activeQueueExecutor()) {
            initializeQueueExecutor();
        }
    }

    private void initializeQueueExecutor() {
        this.queueExecutor = Executors.newScheduledThreadPool(1, Thread.ofVirtual()
                .name("RedisBridge-QueuePublisher")
                .factory());
    }

    @Override
    public void load() {
        this.responseReceptionHandler = new ResponseReceptionHandlerImpl(this.redisBridgeClient, this.redisBridgeClient.getExecutorService(), this.connection, this.connection.async(), this.settings.responseTimeoutSeconds());
        this.responseReceptionHandler.load();

        this.ackDeserializer = new AckDeserializerImpl(this.redisBridgeClient, this.connection, this.settings.ackTimeoutSeconds());
        this.ackDeserializer.load();

        if (this.queueExecutor != null) {
            this.queueExecutor.scheduleAtFixedRate(this::processBatchPublish, this.settings.queuePublishDelayMillis(), this.settings.queuePublishDelayMillis(), TimeUnit.MILLISECONDS);
        }
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
        if (this.responseReceptionHandler != null) {
            this.responseReceptionHandler.unload();
            this.responseReceptionHandler = null;
        }

        if (this.ackDeserializer != null) {
            this.ackDeserializer.unload();
            this.ackDeserializer = null;
        }
    }

    private void processBatchPublish() {
        if (messageQueue.isEmpty())
            return;

        var async = this.connection.async();

        QueuedMessage<?> queuedMessage;
        while ((queuedMessage = messageQueue.poll()) != null) {
            processQueueMessage(async, queuedMessage);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processQueueMessage(RedisAsyncCommands<String, String> async, QueuedMessage<?> queuedMessage) {
        try {
            CompletableFuture<UUID> ackFuture = queuedMessage.message.ackRequested()
                    ? this.ackDeserializer.expectAck(queuedMessage.message.uniqueId())
                    : null;

            Packet<?> finalPacket = queuedMessage.message;
            for (MessageInterceptor interceptor : this.redisBridgeClient.interceptors()) {
                finalPacket = interceptor.onSend(finalPacket);
            }

            async.publish(queuedMessage.receiver.channel(), this.messagingService.serialize(finalPacket))
                    .whenComplete((count, throwable) -> {
                        if (throwable != null) {
                            if (ackFuture != null) ackFuture.completeExceptionally(throwable);
                            queuedMessage.future.completeExceptionally(throwable);
                        } else if (ackFuture == null) {
                            queuedMessage.future.complete((Packet) queuedMessage.message);
                        }
                    });

            if (ackFuture != null) {
                ackFuture.thenAccept(msg -> queuedMessage.future.complete((Packet) queuedMessage.message))
                        .exceptionally(throwable -> {
                            queuedMessage.future.completeExceptionally(throwable);
                            return null;
                        });
            }
        } catch (Exception e) {
            queuedMessage.future.completeExceptionally(e);
        }
    }

    @Override
    public <M extends Message> CompletableFuture<Packet<M>> publish(@NotNull M message, @NotNull MessageEntity receiver) {
        Packet<M> packet = new PacketImpl<>(UUID.randomUUID(), this.sender, message);

        for (MessageInterceptor interceptor : this.redisBridgeClient.interceptors()) {
            packet = interceptor.onSend(packet);
        }

        CompletableFuture<Packet<M>> resultFuture = new CompletableFuture<>();
        CompletableFuture<UUID> ackFuture = packet.ackRequested() ? this.ackDeserializer.expectAck(packet.uniqueId()) : null;

        String jsonMessage = this.messagingService.serialize(packet);
        final Packet<M> finalActionMessage = packet;
        this.connection.async().publish(receiver.channel(), jsonMessage)
                .whenComplete((count, throwable) -> {
                    if (throwable != null) {
                        if (ackFuture != null) ackFuture.completeExceptionally(throwable);
                        resultFuture.completeExceptionally(throwable);
                    } else if (ackFuture == null) {
                        resultFuture.complete(finalActionMessage);
                    }
                });

        if (ackFuture != null) {
            ackFuture.thenAccept(id -> resultFuture.complete(finalActionMessage))
                    .exceptionally(throwable -> {
                        resultFuture.completeExceptionally(throwable);
                        return null;
                    });
        }

        return resultFuture;
    }

    @Override
    public <M extends Message> CompletableFuture<Packet<M>> publishQueued(@NotNull M message, @NotNull MessageEntity receiver) {
        if (this.queueExecutor == null) {
            throw new IllegalStateException("Queue executor is not initialized. Enable activeQueueExecutor in RedisBridgeClient constructor.");
        }

        PacketImpl<M> actionMessage = new PacketImpl<>(UUID.randomUUID(), this.sender, message);
        CompletableFuture<Packet<M>> future = new CompletableFuture<>();

        this.messageQueue.offer(new QueuedMessage<>(actionMessage, receiver, future));

        return future;
    }

    @Override
    public <M extends Message, R extends Response> void publishResponse(@NotNull PacketResponse<M, R> messageResponse, @NotNull MessageEntity receiver) {
        this.connection.async().publish(receiver.channel(), this.messagingService.serialize(messageResponse));
    }

    @Override
    public <M extends Message, R extends Response> void publishResponse(@NotNull Packet<M> original, @NotNull R response, @NotNull MessageEntity receiver) {
        this.publishResponse(PacketResponseImpl.<M, R>builder().originalMessage(original).response(response).build(), receiver);
    }

    @Override
    public <M extends Message, R extends Response> CompletableFuture<PacketResponse<M, R>> waitResponse(@NotNull M message, @NotNull MessageEntity receiver) {
        Packet<M> packet = new PacketImpl<>(UUID.randomUUID(), this.sender, message);

        for (MessageInterceptor interceptor : this.redisBridgeClient.interceptors()) {
            packet = interceptor.onSend(packet);
        }

        // Register response future immediately to avoid race conditions with ACK
        CompletableFuture<PacketResponse<M, R>> responseFuture = this.responseReceptionHandler.handle(packet);

        String jsonMessage = this.messagingService.serialize(packet);
        final Packet<M> finalPacket = packet;
        this.connection.async().publish(receiver.channel(), jsonMessage)
                .whenComplete((count, throwable) -> {
                    if (throwable != null) {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), throwable);
                    } else if (count == null || count == 0) {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), new IllegalStateException("No subscribers received the message: " + finalPacket));
                    }
                });

        if (packet.ackRequested()) {
            this.ackDeserializer.expectAck(packet.uniqueId())
                    .exceptionally(throwable -> {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), throwable);
                        return null;
                    });
        }

        return responseFuture;
    }

    @Override
    public <M extends Message, R extends Response> CompletableFuture<List<PacketResponse<M, R>>> waitResponses(@NotNull M message, @NotNull MessageEntity receiver, boolean includeSender) {
        Packet<M> packet = new PacketImpl<>(UUID.randomUUID(), this.sender, message);

        for (MessageInterceptor interceptor : this.redisBridgeClient.interceptors()) {
            packet = interceptor.onSend(packet);
        }

        // Register response future immediately to avoid race conditions with ACK
        ResponseReceptionHandler.MultiResponseCollector<M, R> collector = this.responseReceptionHandler.handleMultiple(packet);

        String jsonMessage = this.messagingService.serialize(packet);
        final Packet<M> finalPacket = packet;
        this.connection.async().publish(receiver.channel(), jsonMessage)
                .whenComplete((count, throwable) -> {
                    int expectedCount = (count != null ? count.intValue() : 0) - (includeSender ? 0 : 1);

                    if (throwable != null) {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), throwable);
                    } else if (expectedCount <= 0) {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), new IllegalStateException("No subscribers received the message: " + finalPacket));
                    } else {
                        collector.setExpectedResponses(expectedCount);
                    }
                });

        if (packet.ackRequested()) {
            this.ackDeserializer.expectAck(packet.uniqueId())
                    .exceptionally(throwable -> {
                        this.responseReceptionHandler.cancel(finalPacket.uniqueId(), throwable);
                        return null;
                    });
        }

        return collector.getFuture();
    }

    private record QueuedMessage<T extends Message>(Packet<T> message, MessageEntity receiver, CompletableFuture<Packet<T>> future) {
    }
}
