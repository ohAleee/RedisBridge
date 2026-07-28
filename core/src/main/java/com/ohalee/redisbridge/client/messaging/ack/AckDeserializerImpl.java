package com.ohalee.redisbridge.client.messaging.ack;

import com.ohalee.redisbridge.api.messaging.ack.exception.NoAckException;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.AbstractMessageHandler;
import com.ohalee.redisbridge.client.messaging.RedisMessagingService;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal component handling ACK subscriptions and correlation of ACKs for published messages.
 *
 * <p>Extends {@link AbstractMessageHandler} so incoming ACK payloads are deserialized on the
 * shared executor rather than on the Lettuce event-loop thread.</p>
 */
public class AckDeserializerImpl extends AbstractMessageHandler {

    private final Map<UUID, CompletableFuture<UUID>> waitingAck = new ConcurrentHashMap<>();
    private final String channel;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final RedisMessagingService messagingService;
    private final int timeoutSeconds;
    private boolean loaded;

    public AckDeserializerImpl(RedisBridgeClient client, ExecutorService executorService,
                               StatefulRedisPubSubConnection<String, String> pubSubConnection, int timeoutSeconds) {
        super(client, executorService);
        this.channel = client.channels().ack(client.clientId()).channel();
        this.pubSubConnection = pubSubConnection;
        this.messagingService = client.getMessagingService();
        this.timeoutSeconds = timeoutSeconds;

        this.addChannel(this.channel);
    }

    public synchronized void load() {
        if (this.loaded) return;
        this.loaded = true;
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.async().subscribe(this.channel);
    }

    public synchronized void unload() {
        if (!this.loaded) return;
        this.loaded = false;
        this.pubSubConnection.removeListener(this);
        this.pubSubConnection.async().unsubscribe(this.channel);
        this.waitingAck.clear();
    }

    @Override
    protected void handleIncomingMessage(String channel, String message) {
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
