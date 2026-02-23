package com.ohalee.redisbridge.client.messaging.response;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import com.ohalee.redisbridge.api.messaging.response.ResponseReceptionHandler;
import com.ohalee.redisbridge.api.messaging.response.exception.NoResponseException;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.AbstractMessageHandler;
import com.ohalee.redisbridge.client.messaging.RedisMessagingService;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class ResponseReceptionHandlerImpl extends AbstractMessageHandler implements ResponseReceptionHandler {

    private final Map<UUID, CompletableFuture<?>> waitingResponse = new ConcurrentHashMap<>();
    private final Map<UUID, MultiResponseCollectorImpl<?, ?>> waitingMultiResponse = new ConcurrentHashMap<>();
    private final MessageRegistry messageRegistry;
    private final RedisMessagingService messagingService;
    private final String channel;
    private final StatefulRedisPubSubConnection<String, String> connection;
    private final RedisPubSubAsyncCommands<String, String> commands;
    private final int responseTimeoutSeconds;

    public ResponseReceptionHandlerImpl(RedisBridgeClient client, ExecutorService executorService,
                                        StatefulRedisPubSubConnection<String, String> connection, RedisPubSubAsyncCommands<String, String> commands,
                                        int responseTimeoutSeconds) {
        super(client, executorService);
        this.messageRegistry = client.getMessageRegistry();
        this.messagingService = client.getMessagingService();
        this.channel = MessageEntity.response(client.clientId()).channel();
        this.connection = connection;
        this.commands = commands;
        this.responseTimeoutSeconds = responseTimeoutSeconds;

        this.addChannel(this.channel);
    }

    @Override
    public void load() {
        this.connection.addListener(this);
        this.commands.subscribe(this.channel);
    }

    @Override
    public void unload() {
        this.connection.removeListener(this);
        this.commands.unsubscribe(this.channel);
        this.waitingResponse.clear();
        this.waitingMultiResponse.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleIncomingMessage(String channel, String message) {
        if (!this.channel.equals(channel)) return;

        PacketResponse<?, ?> response = this.messagingService.deserialize(message, PacketResponseImpl.class);
        Packet<?> packet = response.packet();

        String namespace = MessageRegistry.getNamespace(packet.message());
        MessageRegistration registration = this.messageRegistry.getRegistration(namespace);

        if (registration == null || !registration.expectsResponse())
            throw new IllegalStateException("Received a response for unregistered message namespace " + namespace);

        ResponseMessageHandler<Message, Response> handler = registration.responseHandler();
        if (handler != null) {
            handler.handleResponse((PacketResponse<Message, Response>) response);
        }

        CompletableFuture<PacketResponse<Message, Response>> future = (CompletableFuture<PacketResponse<Message, Response>>) this.waitingResponse.remove(packet.uniqueId());
        if (future != null) {
            future.complete((PacketResponse<Message, Response>) response);
            return;
        }

        MultiResponseCollectorImpl<Message, Response> multiCollector = (MultiResponseCollectorImpl<Message, Response>) this.waitingMultiResponse.get(packet.uniqueId());
        if (multiCollector != null) {
            multiCollector.addResponse((PacketResponse<Message, Response>) response);
        }
    }

    @Override
    public <M extends Message, R extends Response> CompletableFuture<PacketResponse<M, R>> handle(@NotNull Packet<M> message) {
        CompletableFuture<PacketResponse<M, R>> future = new CompletableFuture<PacketResponse<M, R>>()
                .orTimeout(this.responseTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionallyCompose(throwable -> {
                    this.waitingResponse.remove(message.uniqueId());
                    return CompletableFuture.failedFuture(throwable instanceof TimeoutException ? new NoResponseException() : throwable);
                });
        this.waitingResponse.put(message.uniqueId(), future);
        return future;
    }

    @Override
    public <M extends Message, R extends Response> CompletableFuture<PacketResponse<M, R>> handle(CompletableFuture<Packet<M>> message) {
        return message.thenCompose(this::handle);
    }

    @Override
    public <M extends Message, R extends Response> MultiResponseCollector<M, R> handleMultiple(@NotNull Packet<M> message) {
        MultiResponseCollectorImpl<M, R> collector = new MultiResponseCollectorImpl<>();
        CompletableFuture<List<PacketResponse<M, R>>> future = collector.getFuture()
                .orTimeout(this.responseTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionallyCompose(throwable -> {
                    this.waitingMultiResponse.remove(message.uniqueId());
                    return CompletableFuture.failedFuture(throwable instanceof TimeoutException ? new NoResponseException() : throwable);
                });

        future.whenComplete((res, err) -> this.waitingMultiResponse.remove(message.uniqueId()));
        this.waitingMultiResponse.put(message.uniqueId(), collector);
        return collector;
    }

    @Override
    public void cancel(@NotNull UUID uniqueId, @NotNull Throwable cause) {
        CompletableFuture<?> future = this.waitingResponse.remove(uniqueId);
        if (future != null) {
            future.completeExceptionally(cause);
            return;
        }
        MultiResponseCollectorImpl<?, ?> multiCollector = this.waitingMultiResponse.remove(uniqueId);
        if (multiCollector != null) {
            multiCollector.getFuture().completeExceptionally(cause);
        }
    }

    private static class MultiResponseCollectorImpl<M extends Message, R extends Response> implements MultiResponseCollector<M, R> {
        private final CompletableFuture<List<PacketResponse<M, R>>> future = new CompletableFuture<>();
        private final List<PacketResponse<M, R>> responses = new ArrayList<>();
        private int expectedCount = -1;

        public synchronized void addResponse(PacketResponse<M, R> response) {
            this.responses.add(response);
            checkCompletion();
        }

        @Override
        public synchronized void setExpectedResponses(int count) {
            this.expectedCount = count;
            checkCompletion();
        }

        @Override
        public CompletableFuture<List<PacketResponse<M, R>>> getFuture() {
            return this.future;
        }

        private void checkCompletion() {
            if (this.expectedCount >= 0 && this.responses.size() >= this.expectedCount) {
                this.future.complete(new ArrayList<>(this.responses));
            }
        }
    }
}
