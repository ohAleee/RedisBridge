package com.ohalee.redisbridge.models;

import com.ohalee.redisbridge.api.messaging.response.BaseResponse;

public record TestResponse(String response) implements BaseResponse {
}
