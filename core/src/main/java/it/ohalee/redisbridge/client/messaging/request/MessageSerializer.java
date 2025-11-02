package it.ohalee.redisbridge.client.messaging.request;

import com.google.gson.*;
import it.ohalee.redisbridge.api.messaging.Sender;
import it.ohalee.redisbridge.api.messaging.request.Message;
import it.ohalee.redisbridge.api.messaging.request.BaseMessage;
import it.ohalee.redisbridge.api.messaging.request.MessageRegistration;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Type;
import java.util.UUID;

@RequiredArgsConstructor
public class MessageSerializer implements JsonSerializer<Message<BaseMessage>>, JsonDeserializer<Message<BaseMessage>> {

    private final MessageRegistryImpl registry;

    @Override
    public JsonElement serialize(Message<BaseMessage> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("uniqueId", src.uniqueId().toString());

        JsonObject senderObject = new JsonObject();
        senderObject.addProperty("id", src.sender().id());
        senderObject.addProperty("channel", src.sender().entity().channel());
        json.add("sender", senderObject);

        JsonElement messageJson = context.serialize(src.message());
        messageJson.getAsJsonObject().addProperty("namespace", src.message().namespace());

        json.add("message", messageJson);
        return json;
    }

    @Override
    public Message<BaseMessage> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        JsonObject messageObject = jsonObject.getAsJsonObject("message");
        String namespace = messageObject.get("namespace").getAsString();

        if (!registry.isRegistered(namespace))
            throw new JsonParseException("Unregistered message namespace: " + namespace);

        MessageRegistration registration = registry.getRegistration(namespace);
        if (registration == null)
            throw new JsonParseException("No registration found for message namespace: " + namespace);

        BaseMessage originalBaseMessage = context.deserialize(messageObject, registration.messageClass());

        String uniqueId = jsonObject.get("uniqueId").getAsString();

        JsonObject senderObject = jsonObject.getAsJsonObject("sender");
        String registrationID = senderObject.get("id").getAsString();
        String channel = senderObject.get("channel").getAsString();

        Sender sender = Sender.from(registrationID, () -> channel);

        return MessageImpl.builder()
                .uniqueId(UUID.fromString(uniqueId))
                .sender(sender)
                .message(originalBaseMessage)
                .build();
    }
}