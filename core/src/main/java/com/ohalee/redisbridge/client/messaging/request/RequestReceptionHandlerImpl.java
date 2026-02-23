package com.ohalee.redisbridge.client.messaging.request;

import com.google.gson.JsonObject;
import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.interceptor.MessageInterceptor;
import com.ohalee.redisbridge.api.messaging.request.*;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.AbstractMessageHandler;
import com.ohalee.redisbridge.client.messaging.RedisMessagingService;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestReceptionHandlerImpl extends AbstractMessageHandler implements RequestReceptionHandler {

    private static final Logger LOGGER = Logger.getLogger("RedisBidge-Reception-Handler");

    private final MessageRegistry messageRegistry;
    private final RedisMessagingService messagingService;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public RequestReceptionHandlerImpl(RedisBridgeClient client, ExecutorService executorService, StatefulRedisPubSubConnection<String, String> pubSubConnection) {
        super(client, executorService);
        this.messageRegistry = client.getMessageRegistry();
        this.messagingService = client.getMessagingService();
        this.pubSubConnection = pubSubConnection;
    }

    @Override
    public void load() {
        this.pubSubConnection.addListener(this);
        this.pubSubConnection.async().subscribe(this.subscribedChannels().toArray(new String[0]));
    }

    @Override
    public void unload() {
        this.pubSubConnection.removeListener(this);
        this.pubSubConnection.async().unsubscribe(this.subscribedChannels().toArray(new String[0]));
    }

    @Override
    public void subscribe(@NotNull MessageEntity entity) {
        this.addChannel(entity.channel());
    }

    @Override
    public void unsubscribe(@NotNull MessageEntity entity) {
        this.removeChannel(entity.channel());
    }

    @Override
    protected void handleIncomingMessage(String channel, String messageRaw) {
        try {
            JsonObject jsonMessage = this.messagingService.deserialize(messageRaw, JsonObject.class);

            if (jsonMessage.has("ack") && jsonMessage.get("ack").getAsBoolean()) {
                handleAck(jsonMessage);
            }

            if (jsonMessage.has("message")) {
                JsonObject actionObj = jsonMessage.getAsJsonObject("message");
                if (actionObj.has("namespace")) {
                    String namespace = actionObj.get("namespace").getAsString();
                    handleRequest(namespace, jsonMessage);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing incoming request message", e);
        }
    }

    private void handleAck(JsonObject jsonMessage) {
        try {
            if (!jsonMessage.has("uniqueId") || !jsonMessage.has("sender"))
                return;

            String id = jsonMessage.get("uniqueId").getAsString();
            JsonObject sender = jsonMessage.getAsJsonObject("sender");
            if (!sender.has("id")) return;

            String senderId = sender.get("id").getAsString();

            var ackPayload = new JsonObject();
            ackPayload.addProperty("uniqueId", id);

            this.pubSubConnection.async()
                    .publish(MessageEntity.ack(senderId).channel(), this.messagingService.serialize(ackPayload))
                    .exceptionally(throwable -> {
                        LOGGER.log(Level.WARNING, "Failed to send ACK for message " + id, throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while processing ACK", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRequest(String namespace, JsonObject jsonMessage) {
        MessageRegistration registration = this.messageRegistry.getRegistration(namespace);
        if (registration == null) {
            LOGGER.log(Level.WARNING, "No message registration found for namespace: {0}", namespace);
            return;
        }

        PacketImpl<? extends Message> parsedMessage = parseMessage(jsonMessage, (Class<Message>) registration.messageClass());

        Packet<Message> finalPacket = (Packet<Message>) parsedMessage;
        for (MessageInterceptor interceptor : this.client.interceptors()) {
            finalPacket = interceptor.onReceive(finalPacket);
        }

        MessageHandler<Message> handler = registration.handler();
        if (handler != null) {
            handler.handle(finalPacket);
        }
    }

    @Override
    public final <T extends Message> PacketImpl<T> parseMessage(JsonObject jsonMessage, Class<T> clazz) {
        return this.messagingService.<PacketImpl<T>>deserialize(jsonMessage, PacketImpl.class);
    }

    @Override
    public final <T extends Message> T parseRequest(JsonObject jsonMessage, Class<T> clazz) {
        return this.messagingService.deserialize(jsonMessage.getAsJsonObject("message"), clazz);
    }
}
