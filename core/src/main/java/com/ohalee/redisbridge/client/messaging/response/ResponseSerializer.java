package com.ohalee.redisbridge.client.messaging.response;

import com.google.gson.*;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.messaging.response.Response;
import com.ohalee.redisbridge.client.messaging.request.PacketImpl;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Type;

@RequiredArgsConstructor
public class ResponseSerializer implements JsonSerializer<PacketResponse<Message, Response>>, JsonDeserializer<PacketResponse<Message, Response>> {

    private final MessageRegistry messageRegistry;

    @Override
    public JsonElement serialize(PacketResponse<Message, Response> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.add("packet", context.serialize(src.packet(), PacketImpl.class));
        json.add("response", context.serialize(src.response()));
        return json;
    }

    @Override
    public PacketResponse<Message, Response> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        PacketImpl<Message> packet = context.deserialize(jsonObject.get("packet"), PacketImpl.class);

        String namespace = MessageRegistry.getNamespace(packet.message());
        if (!this.messageRegistry.isRegistered(namespace))
            throw new IllegalStateException("Unregistered message namespace: " + namespace);

        MessageRegistration registration = this.messageRegistry.getRegistration(namespace);
        if (registration == null)
            throw new IllegalStateException("No registration found for message namespace: " + namespace);

        JsonObject responseObject = jsonObject.getAsJsonObject("response");
        Response response = context.deserialize(responseObject, registration.responseClass());

        return PacketResponseImpl.builder()
                .originalMessage(packet)
                .response(response)
                .build();
    }
}
