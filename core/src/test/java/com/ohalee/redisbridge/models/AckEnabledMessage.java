package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageName;

@MessageName("test:ack")
public record AckEnabledMessage(String payload) implements Message {

    @Override
    public boolean ackEnabled() {
        return true;
    }
}
