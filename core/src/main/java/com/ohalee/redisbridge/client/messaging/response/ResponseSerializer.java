package com.ohalee.redisbridge.client.messaging.response;

import com.google.gson.*;
import com.ohalee.redisbridge.api.messaging.request.BaseMessage;
import com.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import com.ohalee.redisbridge.api.messaging.response.MessageResponse;
import com.ohalee.redisbridge.api.messaging.response.BaseResponse;
import com.ohalee.redisbridge.client.messaging.request.MessageImpl;
import com.ohalee.redisbridge.client.messaging.request.MessageRegistryImpl;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Type;

@RequiredArgsConstructor
public class ResponseSerializer implements JsonSerializer<MessageResponse<BaseMessage, BaseResponse>>, JsonDeserializer<MessageResponse<BaseMessage, BaseResponse>> {

    private final MessageRegistryImpl registry;

    @Override
    public JsonElement serialize(MessageResponse<BaseMessage, BaseResponse> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.add("originalMessage", context.serialize(src.originalMessage(), MessageImpl.class));
        json.add("response", context.serialize(src.response()));
        return json;
    }

    @Override
    public MessageResponse<BaseMessage, BaseResponse> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        MessageImpl<BaseMessage> originalMessage = context.deserialize(jsonObject.get("originalMessage"), MessageImpl.class);

        String namespace = originalMessage.message().namespace();
        if (!registry.isRegistered(namespace))
            throw new JsonParseException("Unregistered message namespace: " + namespace);

        MessageRegistration registration = registry.getRegistration(namespace);
        if (registration == null)
            throw new JsonParseException("No registration found for message namespace: " + namespace);

        JsonObject responseObject = jsonObject.getAsJsonObject("response");
        BaseResponse response = context.deserialize(responseObject, registration.responseClass());

        return MessageResponseImpl.builder()
                .originalMessage(originalMessage)
                .response(response)
                .build();
    }
}