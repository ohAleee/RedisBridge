package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.response.Response;

public record TestResponse(String response) implements Response {
}
