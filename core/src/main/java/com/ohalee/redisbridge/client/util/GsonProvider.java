package com.ohalee.redisbridge.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ohalee.redisbridge.client.messaging.request.MessageSerializer;
import com.ohalee.redisbridge.client.messaging.request.MessageImpl;
import com.ohalee.redisbridge.client.messaging.request.MessageRegistryImpl;
import com.ohalee.redisbridge.client.messaging.response.ResponseSerializer;
import com.ohalee.redisbridge.client.messaging.response.MessageResponseImpl;

public class GsonProvider {

    private static Gson NORMAL;

    public static void initialize(MessageRegistryImpl registry) {
        NORMAL = new GsonBuilder()
                .disableHtmlEscaping()
                .registerTypeAdapter(MessageImpl.class, new MessageSerializer(registry))
                .registerTypeAdapter(MessageResponseImpl.class, new ResponseSerializer(registry))
                .create();
    }

    private GsonProvider() {
        throw new AssertionError();
    }

    public static Gson normal() {
        return NORMAL;
    }

}
