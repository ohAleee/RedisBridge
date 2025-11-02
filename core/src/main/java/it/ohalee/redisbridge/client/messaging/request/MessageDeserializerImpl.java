package it.ohalee.redisbridge.client.messaging.request;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import it.ohalee.redisbridge.api.messaging.MessageEntity;
import it.ohalee.redisbridge.api.messaging.redis.RedisMessageListener;
import it.ohalee.redisbridge.api.messaging.request.*;
import it.ohalee.redisbridge.client.util.GsonProvider;
import org.jetbrains.annotations.Async;

import java.util.concurrent.ExecutorService;

public class MessageDeserializerImpl implements RedisMessageListener, MessageDeserializer {

    private final ExecutorService executorService;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final String channel;
    private final MessageRegistryImpl registry;

    public MessageDeserializerImpl(ExecutorService executorService, StatefulRedisPubSubConnection<String, String> pubSubConnection, MessageEntity entity, MessageRegistryImpl registry) {
        this.executorService = executorService;
        this.pubSubConnection = pubSubConnection;
        this.channel = entity.channel();
        this.registry = registry;
    }

    @Override
    public void load() {
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.async().subscribe(this.channel);
    }

    @Override
    public void unload() {
        this.pubSubConnection.removeListener(this);
        this.pubSubConnection.async().unsubscribe(this.channel);
    }

    @Override
    public final void message(String channel, String message) {
        if (!this.channel.equals(channel)) return;

        JsonObject jsonMessage = GsonProvider.normal().fromJson(message, JsonObject.class);
        JsonObject actionObj = jsonMessage.getAsJsonObject("message");
        String namespace = actionObj.get("namespace").getAsString();

        this.executorService.execute(() -> handleMessage(namespace, jsonMessage));
    }

    /**
     * Handle incoming messages.
     *
     * @param namespace   The namespace identifier of the Redis message
     * @param jsonMessage The full JSON message
     */
    @Async.Execute
    private void handleMessage(String namespace, JsonObject jsonMessage) {
        MessageRegistration registration = registry.getRegistration(namespace);
        if (registration == null)
            throw new IllegalArgumentException("No message registration found for namespace: " + namespace);

        MessageImpl<? extends BaseMessage> parsedMessage = parseMessage(jsonMessage, registration.messageClass());

        MessageHandler<BaseMessage> handler = (MessageHandler<BaseMessage>) registration.handler();

        if (handler != null) {
            handler.handle((Message<BaseMessage>) parsedMessage);
        }
    }

    @Override
    public final <T extends BaseMessage> MessageImpl<T> parseMessage(JsonObject jsonMessage, Class<T> clazz) {
        return GsonProvider.normal().fromJson(jsonMessage, MessageImpl.class);
    }

    @Override
    public final <T extends BaseMessage> T parseRequest(JsonObject jsonMessage, Class<T> clazz) {
        return parseAction(jsonMessage.getAsJsonObject("message"), clazz);
    }

    private static <T extends BaseMessage> T parseAction(JsonElement element, Class<T> clazz) {
        return GsonProvider.normal().fromJson(element, clazz);
    }
}
