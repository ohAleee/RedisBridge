package com.ohalee.redisbridge.client.messaging.request;

import com.google.gson.*;
import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Type;
import java.util.UUID;

@RequiredArgsConstructor
public class PacketSerializer implements JsonSerializer<Packet<Message>>, JsonDeserializer<Packet<Message>> {

    private final MessageRegistry messageRegistry;

    @Override
    public JsonElement serialize(Packet<Message> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("uniqueId", src.uniqueId().toString());
        json.addProperty("ack", src.ackRequested());

        JsonObject senderObject = new JsonObject();
        senderObject.addProperty("id", src.sender().id());
        senderObject.addProperty("channel", src.sender().entity().channel());
        json.add("sender", senderObject);

        JsonElement messageJson = context.serialize(src.message());
        messageJson.getAsJsonObject().addProperty("namespace", MessageRegistry.getNamespace(src.message()));

        json.add("message", messageJson);
        return json;
    }

    @Override
    public Packet<Message> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        JsonObject messageObject = jsonObject.getAsJsonObject("message");
        String namespace = messageObject.get("namespace").getAsString();

        if (!this.messageRegistry.isRegistered(namespace))
            throw new IllegalStateException("Unregistered message namespace: " + namespace);

        MessageRegistration registration = this.messageRegistry.getRegistration(namespace);
        if (registration == null)
            throw new IllegalStateException("No registration found for message namespace: " + namespace);

        Message originalBaseMessage = context.deserialize(messageObject, registration.messageClass());

        String uniqueId = jsonObject.get("uniqueId").getAsString();

        JsonObject senderObject = jsonObject.getAsJsonObject("sender");
        String registrationID = senderObject.get("id").getAsString();
        String channel = senderObject.get("channel").getAsString();

        return PacketImpl.builder()
                .uniqueId(UUID.fromString(uniqueId))
                .sender(Sender.from(registrationID, () -> channel))
                .message(originalBaseMessage)
                .build();
    }
}