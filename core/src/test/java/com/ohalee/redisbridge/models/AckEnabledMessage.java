package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.BaseMessage;

public record AckEnabledMessage(String payload) implements BaseMessage {
    public static final String NAMESPACE = "test:ack";

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public boolean ackEnabled() {
        return true;
    }
}
