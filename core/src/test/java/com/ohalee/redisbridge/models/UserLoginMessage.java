package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageName;

@MessageName("user:login")
public record UserLoginMessage(String username, long timestamp) implements Message {
    public static final String NAMESPACE = "user:login";
}
