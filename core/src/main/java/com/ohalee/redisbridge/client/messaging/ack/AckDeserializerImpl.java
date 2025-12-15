package com.ohalee.redisbridge.client.messaging.ack;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.ack.exception.NoAckException;
import com.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.cache.CaffeineFactory;
import com.ohalee.redisbridge.client.util.GsonProvider;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Internal component handling ACK subscriptions and correlation of ACKs for published messages.
 */
public class AckDeserializerImpl implements RedisMessageListener {

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, Thread.ofVirtual()
            .name("redisbridge-ack-timeout-%d", 1)
            .factory());

    private final Cache<UUID, CompletableFuture<UUID>> ackCallbacks;
    private final String channel;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public AckDeserializerImpl(RedisBridgeClient client, StatefulRedisPubSubConnection<String, String> pubSubConnection) {
        this.channel = MessageEntity.ack(client.serverID()).channel();
        this.pubSubConnection = pubSubConnection;

        int timeoutSeconds = Integer.getInteger("redisbridge.ack.timeout.seconds",
                Integer.parseInt(System.getenv().getOrDefault("REDISBRIDGE_ACK_TIMEOUT_SECONDS", "5")));

        this.ackCallbacks = CaffeineFactory.newBuilder()
                .expireAfterWrite(timeoutSeconds, TimeUnit.SECONDS)
                .scheduler(Scheduler.forScheduledExecutorService(SCHEDULER))
                .evictionListener((key, value, cause) -> {
                    if (!cause.wasEvicted()) return;
                    CompletableFuture<?> stage;
                    if ((stage = (CompletableFuture<?>) value) != null) {
                        stage.completeExceptionally(new NoAckException());
                    }
                }).build();
    }

    public void load() {
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.async().subscribe(this.channel);
    }

    public void unload() {
        this.pubSubConnection.removeListener(this);
        this.pubSubConnection.async().unsubscribe(this.channel);
        this.ackCallbacks.invalidateAll();
    }

    @Override
    public void message(String channel, String message) {
        if (!this.channel.equals(channel)) return;

        var json = GsonProvider.normal().fromJson(message, Map.class);
        var idObj = json.get("uniqueId");
        if (idObj == null) return;

        UUID id;
        try {
            id = UUID.fromString(idObj.toString());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        CompletableFuture<UUID> future = this.ackCallbacks.getIfPresent(id);
        if (future == null) return;

        future.complete(id);
        this.ackCallbacks.invalidate(id);
    }

    public CompletableFuture<UUID> expectAck(UUID messageId) {
        CompletableFuture<UUID> future = new CompletableFuture<>();
        this.ackCallbacks.put(messageId, future);
        return future;
    }
}
