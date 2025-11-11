package com.ohalee.redisbridge.client.messaging.response;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import com.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.response.MessageResponse;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.api.messaging.response.ResponseMessageHandler;
import com.ohalee.redisbridge.api.messaging.response.ResponseDeserializer;
import com.ohalee.redisbridge.api.messaging.response.exception.NoResponseException;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.cache.CaffeineFactory;
import com.ohalee.redisbridge.client.util.GsonProvider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.*;

public class ResponseDeserializerImpl implements RedisMessageListener, ResponseDeserializer {

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, Thread.ofVirtual()
            .name("redisbridge-response-timeout-%d", 1)
            .factory());

    private final Cache<@NotNull UUID, CompletableFuture<? extends MessageResponse<?, ?>>> callbackCache = CaffeineFactory.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .scheduler(Scheduler.forScheduledExecutorService(SCHEDULER))
            .evictionListener((key, value, cause) -> {
                if (!cause.wasEvicted()) return;
                CompletableFuture<?> stage;
                if ((stage = (CompletableFuture<?>) value) != null) {
                    stage.completeExceptionally(new NoResponseException());
                }
            }).build();

    private final RedisBridgeClient client;
    private final String channel;
    private final StatefulRedisPubSubConnection<String, String> connection;
    private final RedisPubSubAsyncCommands<String, String> commands;

    public ResponseDeserializerImpl(RedisBridgeClient client, StatefulRedisPubSubConnection<String, String> connection, RedisPubSubAsyncCommands<String, String> commands) {
        this.client = client;
        this.channel = MessageEntity.response(client.serverID()).channel();
        this.connection = connection;
        this.commands = commands;
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
        this.callbackCache.invalidateAll();
    }

    @Override
    public final void message(String channel, String message) {
        if (!this.channel.equals(channel)) return;

        this.client.getExecutorService().execute(() -> handleCallbackMessage(message));
    }

    @Override
    public <M extends BaseMessage, R extends BaseResponse> CompletableFuture<MessageResponse<M, R>> handle(CompletionStage<Message<M>> message) {
        CompletableFuture<MessageResponse<M, R>> future = new CompletableFuture<>();
        message.whenComplete((a, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }
            this.callbackCache.put(a.uniqueId(), future);
        });
        return future;
    }

    private void handleCallbackMessage(String message) {
        MessageResponse<?, ?> callbackMessage = GsonProvider.normal().fromJson(message, MessageResponseImpl.class);
        Message<?> originalMessage = callbackMessage.originalMessage();

        String namespace = originalMessage.message().namespace();
        MessageRegistration registration = this.client.getRegistry().getRegistration(namespace);

        if (registration == null || !registration.expectsResponse())
            throw new IllegalStateException("Received a response for unregistered message namespace " + namespace);

        ResponseMessageHandler<?, ?> handler = registration.responseHandler();
        if (handler != null) {
            ((ResponseMessageHandler) handler).handleResponse(callbackMessage);
        }

        CompletableFuture<? extends MessageResponse<?, ?>> future = this.callbackCache.getIfPresent(originalMessage.uniqueId());

        if (future != null) {
            ((CompletableFuture) future).complete(callbackMessage);
            this.callbackCache.invalidate(originalMessage.uniqueId());
        }
    }

}
