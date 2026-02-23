package com.ohalee.redisbridge.client.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.client.messaging.request.PacketImpl;
import com.ohalee.redisbridge.client.messaging.request.PacketSerializer;
import com.ohalee.redisbridge.client.messaging.response.PacketResponseImpl;
import com.ohalee.redisbridge.client.messaging.response.ResponseSerializer;
import lombok.Getter;

import java.lang.reflect.Type;

/**
 * Service for managing message serialization and deserialization.
 */
@Getter
public class RedisMessagingService {

    private final Gson gson;

    private RedisMessagingService(Gson gson) {
        this.gson = gson;
    }

    public static Builder builder(MessageRegistry messageRegistry) {
        return new Builder(messageRegistry);
    }

    public String serialize(Object object) {
        return gson.toJson(object);
    }

    public <T> T deserialize(String json, Class<T> clazz) {
        return this.gson.fromJson(json, clazz);
    }

    public <T> T deserialize(JsonElement json, Class<T> clazz) {
        return this.gson.fromJson(json, clazz);
    }

    public <T> T deserialize(JsonElement json, Type type) {
        return this.gson.fromJson(json, type);
    }

    public static class Builder {
        private final GsonBuilder gsonBuilder;

        private Builder(MessageRegistry messageRegistry) {
            this.gsonBuilder = new GsonBuilder()
                    .disableHtmlEscaping()
                    .registerTypeAdapter(PacketImpl.class, new PacketSerializer(messageRegistry))
                    .registerTypeAdapter(PacketResponseImpl.class, new ResponseSerializer(messageRegistry));
        }

        /**
         * Registers a custom type adapter for the specified type.
         *
         * @param type    the type
         * @param adapter the adapter (can be JsonSerializer, JsonDeserializer,
         *                TypeAdapter, or TypeAdapterFactory)
         * @return this builder
         */
        public Builder registerAdapter(Type type, Object adapter) {
            this.gsonBuilder.registerTypeAdapter(type, adapter);
            return this;
        }

        public RedisMessagingService build() {
            return new RedisMessagingService(this.gsonBuilder.create());
        }
    }

}
