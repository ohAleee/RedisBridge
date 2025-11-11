package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.BaseMessage;

public record UserLoginBaseMessage(String username, long timestamp) implements BaseMessage {
    public static final String NAMESPACE = "user:login";

    @Override
    public String namespace() {
        return NAMESPACE;
    }
}
