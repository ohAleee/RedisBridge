package com.ohalee.redisbridge.client.messaging;

import com.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public abstract class AbstractMessageHandler implements RedisMessageListener {

    protected final RedisBridgeClient client;
    protected final ExecutorService executorService;
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();

    @Override
    public final void message(String channel, String message) {
        if (!isSubscribed(channel))
            return;

        this.executorService.execute(() -> handleIncomingMessage(channel, message));
    }

    protected abstract void handleIncomingMessage(String channel, String message);

    protected void addChannel(String channel) {
        this.subscribedChannels.add(channel);
    }

    protected void removeChannel(String channel) {
        this.subscribedChannels.remove(channel);
    }

    protected boolean isSubscribed(String channel) {
        return this.subscribedChannels.contains(channel);
    }

    protected Set<String> subscribedChannels() {
        return this.subscribedChannels;
    }

}
