package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageName;

@MessageName("test:base")
public record TestMessage(String content) implements Message {
    public static final String NAMESPACE = "test:base";
}
