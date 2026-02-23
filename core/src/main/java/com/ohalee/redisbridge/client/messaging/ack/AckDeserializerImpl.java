package com.ohalee.redisbridge.client.messaging.ack;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.ack.exception.NoAckException;
import com.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.RedisMessagingService;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal component handling ACK subscriptions and correlation of ACKs for published messages.
 */
public class AckDeserializerImpl implements RedisMessageListener {

    private final Map<UUID, CompletableFuture<UUID>> waitingAck = new ConcurrentHashMap<>();
    private final String channel;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final RedisMessagingService messagingService;
    private final int timeoutSeconds;

    public AckDeserializerImpl(RedisBridgeClient client, StatefulRedisPubSubConnection<String, String> pubSubConnection, int timeoutSeconds) {
        this.channel = MessageEntity.ack(client.clientId()).channel();
        this.pubSubConnection = pubSubConnection;
        this.messagingService = client.getMessagingService();
        this.timeoutSeconds = timeoutSeconds;
    }

    public void load() {
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.async().subscribe(this.channel);
    }

    public void unload() {
        this.pubSubConnection.removeListener(this);
        this.pubSubConnection.async().unsubscribe(this.channel);
        this.waitingAck.clear();
    }

    @Override
    public void message(String channel, String message) {
        if (!this.channel.equals(channel)) return;

        var json = this.messagingService.deserialize(message, Map.class);
        var idObj = json.get("uniqueId");
        if (idObj == null) return;

        UUID id;
        try {
            id = UUID.fromString(idObj.toString());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        CompletableFuture<UUID> future = this.waitingAck.remove(id);
        if (future == null) return;

        future.complete(id);
    }

    public CompletableFuture<UUID> expectAck(UUID messageId) {
        CompletableFuture<UUID> future = new CompletableFuture<UUID>()
                .orTimeout(this.timeoutSeconds, TimeUnit.SECONDS)
                .exceptionallyCompose(throwable -> {
                    this.waitingAck.remove(messageId);
                    return CompletableFuture.failedFuture(throwable instanceof TimeoutException ? new NoAckException() : throwable);
                });
        this.waitingAck.put(messageId, future);
        return future;
    }
}
