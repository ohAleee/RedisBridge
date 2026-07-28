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
import com.ohalee.redisbridge.client.redis.RedisPublisher;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class MessageRouterImpl implements MessageRouter {

    private final RedisBridgeClient redisBridgeClient;
    private final RedisMessagingService messagingService;
    private final RedisPublisher publisher;
    private final Sender sender;
    private final Settings settings;
    private final ConcurrentLinkedQueue<QueuedMessage<?>> messageQueue = new ConcurrentLinkedQueue<>();
    private ResponseReceptionHandler responseReceptionHandler;
    private AckDeserializerImpl ackDeserializer;
    private StatefulRedisPubSubConnection<String, String> connection;
    private @Nullable ScheduledExecutorService queueExecutor;
    private boolean loaded;

    public MessageRouterImpl(RedisBridgeClient client, Settings settings) {
        this.redisBridgeClient = client;
        this.messagingService = client.getMessagingService();
        this.publisher = client.getPublisher();
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
    public synchronized void load() {
        if (this.loaded) return;
        this.loaded = true;

        this.responseReceptionHandler = new ResponseReceptionHandlerImpl(this.redisBridgeClient, this.redisBridgeClient.getExecutorService(), this.connection, this.connection.async(), this.settings.responseTimeoutSeconds());
        this.responseReceptionHandler.load();

        this.ackDeserializer = new AckDeserializerImpl(this.redisBridgeClient, this.redisBridgeClient.getExecutorService(), this.connection, this.settings.ackTimeoutSeconds());
        this.ackDeserializer.load();

        if (this.queueExecutor != null) {
            this.queueExecutor.scheduleAtFixedRate(this::processBatchPublish, this.settings.queuePublishDelayMillis(), this.settings.queuePublishDelayMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void unload() {
        if (!this.loaded) return;
        this.loaded = false;

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

        QueuedMessage<?> queuedMessage;
        while ((queuedMessage = messageQueue.poll()) != null) {
            processQueueMessage(queuedMessage);
        }
    }

    private <T extends Message> void processQueueMessage(QueuedMessage<T> queuedMessage) {
        dispatch(queuedMessage.message(), queuedMessage.receiver(), queuedMessage.future());
    }

    /**
     * Runs the send interceptors, publishes the packet, and completes {@code resultFuture}
     * once delivery (and the ACK, when requested) is confirmed. Shared by instant and queued publishing.
     */
    private <M extends Message> void dispatch(Packet<M> packet, MessageEntity receiver, CompletableFuture<Packet<M>> resultFuture) {
        try {
            Packet<M> intercepted = packet;
            for (MessageInterceptor interceptor : this.redisBridgeClient.interceptors()) {
                intercepted = interceptor.onSend(intercepted);
            }
            final Packet<M> finalPacket = intercepted;

            CompletableFuture<UUID> ackFuture = finalPacket.ackRequested()
                    ? this.ackDeserializer.expectAck(finalPacket.uniqueId())
                    : null;

            this.publisher.publish(receiver.channel(), this.messagingService.serialize(finalPacket))
                    .whenComplete((count, throwable) -> {
                        if (throwable != null) {
                            if (ackFuture != null) ackFuture.completeExceptionally(throwable);
                            resultFuture.completeExceptionally(throwable);
                        } else if (ackFuture == null) {
                            resultFuture.complete(finalPacket);
                        }
                    });

            if (ackFuture != null) {
                ackFuture.thenAccept(id -> resultFuture.complete(finalPacket))
                        .exceptionally(throwable -> {
                            resultFuture.completeExceptionally(throwable);
                            return null;
                        });
            }
        } catch (Exception e) {
            resultFuture.completeExceptionally(e);
        }
    }

    @Override
    public <M extends Message> CompletableFuture<Packet<M>> publish(@NotNull M message, @NotNull MessageEntity receiver) {
        Packet<M> packet = new PacketImpl<>(UUID.randomUUID(), this.sender, message);
        CompletableFuture<Packet<M>> resultFuture = new CompletableFuture<>();
        dispatch(packet, receiver, resultFuture);
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
        this.publisher.publish(receiver.channel(), this.messagingService.serialize(messageResponse));
    }

    @Override
    public <M extends Message, R extends Response> void publishResponse(@NotNull Packet<M> original, @NotNull R response, @NotNull MessageEntity receiver) {
        this.publishResponse(PacketResponseImpl.<M, R>builder().originalMessage(original).response(response).build(), receiver);
    }

    /**
     * Replies inside this client's channel namespace, instead of the JVM-wide default one
     * used by {@link MessageRouter#reply(Packet, Response)}.
     */
    @Override
    public <M extends Message, R extends Response> void reply(Packet<M> original, R response) {
        this.publishResponse(original, response, this.redisBridgeClient.channels().response(original.sender()));
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
        this.publisher.publish(receiver.channel(), jsonMessage)
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
        this.publisher.publish(receiver.channel(), jsonMessage)
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
