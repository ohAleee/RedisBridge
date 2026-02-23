package com.ohalee.redisbridge.client;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.MessageRouter;
import com.ohalee.redisbridge.api.messaging.interceptor.MessageInterceptor;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.api.messaging.request.RequestReceptionHandler;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.messaging.MessageRouterImpl;
import com.ohalee.redisbridge.client.messaging.RedisMessagingService;
import com.ohalee.redisbridge.client.messaging.request.MessageRegistryImpl;
import com.ohalee.redisbridge.client.messaging.request.RequestReceptionHandlerImpl;
import lombok.AccessLevel;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public abstract class RedisBridgeClient {

    public static final MessageRegistry MESSAGE_REGISTRY = new MessageRegistryImpl();

    private final MessageRegistry messageRegistry;
    private final RedisMessagingService messagingService;

    @Getter(AccessLevel.NONE)
    private final List<MessageInterceptor> interceptors = new ArrayList<>();

    private final ExecutorService executorService;

    private RedisConnectionProvider redis;
    private RequestReceptionHandler redisListener;
    private MessageRouter redisRouter;

    public RedisBridgeClient() {
        this(Executors.newVirtualThreadPerTaskExecutor());
    }

    public RedisBridgeClient(ExecutorService executorService) {
        this(executorService, MESSAGE_REGISTRY, null);
    }

    protected RedisBridgeClient(ExecutorService executorService, MessageRegistry messageRegistry, RedisMessagingService messagingService) {
        this.executorService = executorService;
        this.messageRegistry = messageRegistry;
        this.messagingService = messagingService != null ? messagingService
                : RedisMessagingService.builder(messageRegistry).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Adds an interceptor to the client.
     *
     * @param interceptor the interceptor to add
     */
    public void addInterceptor(MessageInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    /**
     * Returns an unmodifiable list of registered interceptors.
     *
     * @return the list of interceptors
     */
    public List<MessageInterceptor> interceptors() {
        return this.interceptors;
    }

    public void initialize() {
        this.redis = provideRedisConnector();
        this.redis.connect();

        this.redisRouter = new MessageRouterImpl(this, this.routerSettings());
        this.redisListener = new RequestReceptionHandlerImpl(this, this.executorService, this.redis.pubSubConnection());
    }

    public void load() {
        if (this.redis == null) {
            initialize();
        }

        this.redisListener.subscribe(this.platformEntity());

        this.redisRouter.load();
        this.redisListener.load();
    }

    public void unload() {
        if (this.redisListener != null) {
            this.redisListener.unload();
        }
        if (this.redisRouter != null) {
            this.redisRouter.unload();
        }
        if (this.redis != null) {
            this.redis.disconnect();
        }
        this.executorService.shutdown();
    }

    public MessageEntity platformEntity() {
        return MessageEntity.of(this.clientId());
    }

    public abstract String clientId();

    protected abstract RedisConnectionProvider provideRedisConnector();

    public MessageRouter.Settings routerSettings() {
        return MessageRouter.Settings.defaultSettings();
    }

    public static class Builder {
        private final Map<Type, Object> adapters = new HashMap<>();
        private String clientId;
        private RedisConnectionProvider redisConnector;
        private ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        private MessageRegistry messageRegistry = MESSAGE_REGISTRY;

        public Builder() {
        }

        public Builder messageRegistry(MessageRegistry messageRegistry) {
            this.messageRegistry = messageRegistry;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder redisConnector(RedisConnectionProvider redisConnector) {
            this.redisConnector = redisConnector;
            return this;
        }

        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Builder registerAdapter(Type type, Object adapter) {
            this.adapters.put(type, adapter);
            return this;
        }

        public RedisBridgeClient build() {
            if (this.clientId == null)
                throw new IllegalStateException("clientId must be set");
            if (this.redisConnector == null)
                throw new IllegalStateException("redisConnector must be set");

            RedisMessagingService.Builder messagingBuilder = RedisMessagingService.builder(this.messageRegistry);
            this.adapters.forEach(messagingBuilder::registerAdapter);
            RedisMessagingService messagingService = messagingBuilder.build();

            return new RedisBridgeClient(this.executorService, this.messageRegistry, messagingService) {
                @Override
                public String clientId() {
                    return clientId;
                }

                @Override
                protected RedisConnectionProvider provideRedisConnector() {
                    return redisConnector;
                }
            };
        }
    }
}
