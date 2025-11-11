package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.BaseMessage;

public record TestBaseMessage(String content) implements BaseMessage {

    public static final String NAMESPACE = "test:base";

    @Override
    public String namespace() {
        return NAMESPACE;
    }
}
