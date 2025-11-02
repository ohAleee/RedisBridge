package it.ohalee.redisbridge.client;

import it.ohalee.redisbridge.api.messaging.MessageRouter;
import it.ohalee.redisbridge.api.messaging.MessageEntity;
import it.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import it.ohalee.redisbridge.client.messaging.MessageRouterImpl;
import it.ohalee.redisbridge.client.messaging.request.MessageDeserializerImpl;
import it.ohalee.redisbridge.client.messaging.request.MessageRegistryImpl;
import it.ohalee.redisbridge.client.util.GsonProvider;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public abstract class RedisBridgeClient {

    private final ExecutorService executorService;

    private RedisConnectionProvider redis;
    private MessageDeserializerImpl redisListener;
    private MessageRegistryImpl registry;
    private MessageRouter redisRouter;

    public RedisBridgeClient() {
        this(Executors.newVirtualThreadPerTaskExecutor());
    }

    public RedisBridgeClient(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void load() {
        this.redis = provideRedisConnector();
        this.redis.connect();

        this.registry = new MessageRegistryImpl();

        GsonProvider.initialize(this.registry);

        this.redisRouter = new MessageRouterImpl(this);
        this.redisListener = new MessageDeserializerImpl(this.executorService, this.redis.pubSubConnection(), this.platformEntity(), this.registry);

        this.redisRouter.load();
        this.redisListener.load();
    }

    public void unload() {
        this.redisListener.unload();
        this.redisRouter.unload();
        this.redis.disconnect();
        this.executorService.shutdown();
    }

    public MessageEntity platformEntity() {
        return MessageEntity.of(this.serverID());
    }

    public abstract String serverID();

    protected abstract RedisConnectionProvider provideRedisConnector();

}
