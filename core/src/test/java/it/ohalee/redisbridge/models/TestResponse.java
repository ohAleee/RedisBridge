package it.ohalee.redisbridge.models;

import it.ohalee.redisbridge.api.messaging.response.BaseResponse;

public record TestResponse(String response) implements BaseResponse {
}
