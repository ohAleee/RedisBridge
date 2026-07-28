package com.ohalee.redisbridge.client;

import com.ohalee.redisbridge.api.messaging.MessageChannels;
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
import com.ohalee.redisbridge.client.redis.RedisPublisher;
import lombok.AccessLevel;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public abstract class RedisBridgeClient {

    /**
     * The shared, JVM-wide default {@link MessageRegistry}.
     *
     * <p><b>Note:</b> every client created without an explicit registry shares this
     * single instance, so message namespaces are global across all such clients in
     * the same JVM. Register a namespace only once; registering the same namespace
     * twice throws {@link IllegalStateException}. To isolate registrations per client,
     * supply your own registry via {@link Builder#messageRegistry(MessageRegistry)}.</p>
     */
    public static final MessageRegistry MESSAGE_REGISTRY = new MessageRegistryImpl();

    private final MessageRegistry messageRegistry;
    private final RedisMessagingService messagingService;

    @Getter(AccessLevel.NONE)
    private final List<MessageInterceptor> interceptors = new CopyOnWriteArrayList<>();

    private final ExecutorService executorService;

    private RedisConnectionProvider redis;
    private RedisPublisher publisher;
    private RequestReceptionHandler redisListener;
    private MessageRouter redisRouter;

    @Getter(AccessLevel.NONE)
    private volatile boolean loaded;

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
        return Collections.unmodifiableList(this.interceptors);
    }

    public void initialize() {
        this.redis = provideRedisConnector();
        this.redis.connect();

        this.publisher = new RedisPublisher(this.redis);
        this.redisRouter = new MessageRouterImpl(this, this.routerSettings());
        this.redisListener = new RequestReceptionHandlerImpl(this, this.executorService, this.redis.pubSubConnection());
    }

    public void load() {
        if (this.loaded) {
            return;
        }

        if (this.redis == null) {
            initialize();
        }

        this.redisListener.subscribe(this.platformEntity());

        this.redisRouter.load();
        this.redisListener.load();
        this.loaded = true;
    }

    public void unload() {
        if (!this.loaded) {
            return;
        }
        this.loaded = false;

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

    /**
     * The Redis channel namespace of this client.
     *
     * <p>Every channel this client publishes to or subscribes to lives inside this namespace,
     * so a project can keep its traffic separate from other projects sharing the same Redis
     * server (or the same JVM). Override it to use a project-specific prefix:</p>
     *
     * <pre>{@code
     * @Override
     * public MessageChannels channels() {
     *     return MessageChannels.withPrefix("my-project");
     * }
     * }</pre>
     *
     * <p>Clients built through {@link Builder} configure it with
     * {@link Builder#channelPrefix(String)} or {@link Builder#channels(MessageChannels)}.</p>
     *
     * <p>Defaults to {@link MessageChannels#defaults()}, the JVM-wide namespace.</p>
     *
     * @return the channel namespace used by this client
     */
    public MessageChannels channels() {
        return MessageChannels.defaults();
    }

    public MessageEntity platformEntity() {
        return this.channels().of(this.clientId());
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
        private MessageChannels channels = MessageChannels.defaults();

        public Builder() {
        }

        /**
         * Sets the channel prefix of this client, isolating its traffic from projects
         * using a different prefix.
         *
         * @param channelPrefix the project prefix (e.g. "my-project")
         * @return this builder
         * @throws IllegalArgumentException if the prefix is null or blank
         */
        public Builder channelPrefix(String channelPrefix) {
            return this.channels(MessageChannels.withPrefix(channelPrefix));
        }

        /**
         * Sets the channel namespace of this client.
         *
         * @param channels the namespace every channel of this client belongs to
         * @return this builder
         */
        public Builder channels(MessageChannels channels) {
            if (channels == null)
                throw new IllegalArgumentException("channels must not be null");

            this.channels = channels;
            return this;
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

            MessageChannels channels = this.channels;
            return new RedisBridgeClient(this.executorService, this.messageRegistry, messagingService) {
                @Override
                public String clientId() {
                    return clientId;
                }

                @Override
                public MessageChannels channels() {
                    return channels;
                }

                @Override
                protected RedisConnectionProvider provideRedisConnector() {
                    return redisConnector;
                }
            };
        }
    }
}
