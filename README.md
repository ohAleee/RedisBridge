# RedisBridge
[![Maven Central](https://img.shields.io/maven-central/v/com.ohalee.redis-bridge/api.svg)](https://central.sonatype.com/artifact/com.ohalee.redis-bridge/api)
[![MIT License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/ohAleee/RedisBridge/blob/master/LICENSE)

A lightweight Java library for Redis-based inter-service messaging with request-response pattern support.

## Table of Contents
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Architecture](#architecture)
- [ACK (Acknowledgements)](#ack-acknowledgements)
- [Best Practices](#best-practices)
- [License](#license)

## Features

- 🚀 **Simple API** - Intuitive fluent API for message registration and routing
- 🔒 **Type-Safe** - Compile-time type checking for messages and responses
- 🔄 **Request-Response Pattern** - Built-in support for asynchronous request-response messaging
- 📡 **Pub/Sub** - Fire-and-forget message broadcasting
- ⚡ **Async by Default** - Non-blocking CompletableFuture-based responses
- 🎯 **Namespace-based Routing** - Organized message handling with namespaces
- 🔌 **Flexible Connection** - Bring your own Redis connection provider

## Requirements

- Java 21 or higher
- Redis server

## Installation

RedisBridge is available on Maven Central.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.ohalee.redis-bridge:api:{version}")
    implementation("com.ohalee.redis-bridge:core:{version}")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.ohalee.redis-bridge:api:{version}'
    implementation 'com.ohalee.redis-bridge:core:{version}'
}
```

### Maven

```xml
<dependency>
    <groupId>com.ohalee.redis-bridge</groupId>
    <artifactId>api</artifactId>
    <version>{version}</version>
</dependency>
<dependency>
    <groupId>com.ohalee.redis-bridge</groupId>
    <artifactId>core</artifactId>
    <version>{version}</version>
</dependency>
```

## Quick Start

### 1. Define Your Messages

Messages are simple records implementing `BaseMessage`:

```java
public record UserLoginMessage(String username, long timestamp) implements BaseMessage {
    public static final String NAMESPACE = "user:login";

    @Override
    public String namespace() {
        return NAMESPACE;
    }
}
```

### 2. Define Responses (Optional)

For request-response pattern, create response records implementing `BaseResponse`:

```java
public record LoginResponse(String token, boolean success) implements BaseResponse {
}
```

### 3. Create a Client

```java
RedisBridgeClient client = new RedisBridgeClient() {
    @Override
    public String serverID() {
        return "my-service-1";
    }

    @Override
    protected RedisConnectionProvider provideRedisConnector() {
        return new MyRedisConnectionProvider();
    }
};

client.load();
```

### 4. Register Message Handlers

#### Simple Message (No Response)

```java
client.getRegistry()
    .register(UserLoginMessage.NAMESPACE, UserLoginMessage.class)
    .onReceive(fullMessage -> {
        UserLoginMessage msg = fullMessage.message();
        System.out.println("User logged in: " + msg.username());
    })
    .build();
```

#### Request-Response Pattern

```java
client.getRegistry()
    .register(UserLoginMessage.NAMESPACE, UserLoginMessage.class, LoginResponse.class)
    .onReceive(fullMessage -> {
        // Handle incoming request and send response
        LoginResponse response = new LoginResponse("token-123", true);
        client.getRedisRouter().reply(fullMessage, response);
    })
    .onResponse(fullResponse -> {
        // Handle responses to our requests
        System.out.println("Login response: " + fullResponse.response().success());
    })
    .build();
```

### 5. Send Messages

#### Fire-and-Forget

```java
client.getRedisRouter().publish(
    new UserLoginMessage("john_doe", System.currentTimeMillis()),
    receiver.platformEntity()
);
```

#### Wait for Response

```java
client.getRedisRouter()
    .waitResponse(
        new UserLoginMessage("john_doe", System.currentTimeMillis()),
        receiver.platformEntity(),
        LoginResponse.class
    )
    .thenAccept(fullResponse -> {
        LoginResponse response = fullResponse.response();
        System.out.println("Received: " + response.token());
    })
    .exceptionally(throwable -> {
        System.err.println("Error: " + throwable.getMessage());
        return null;
    });
```

## ACK (Acknowledgements)

RedisBridge can optionally wait for a lightweight delivery acknowledgement (ACK) when publishing a message. This helps ensure that the target service (or channel) actually received the message.

### Enable ACK on a Message

ACK is opt-in and controlled per-message by overriding `ackEnabled()` in your `BaseMessage` implementation:

```java
public record CriticalEvent(String id) implements BaseMessage {
    public static final String NAMESPACE = "events:critical";

    @Override
    public String namespace() { return NAMESPACE; }

    @Override
    public boolean ackEnabled() { return true; }
}
```

When such a message is published, the sender will wait for an ACK from the receiver. The receiver sends ACK automatically upon receipt — no extra code is needed in your handler.

### Publishing with ACK

```java
// Will wait for ACK because CriticalEvent.ackEnabled() == true
client.getRedisRouter()
    .publish(new CriticalEvent("42"), receiver.platformEntity())
    .thenRun(() -> System.out.println("ACK received"))
    .exceptionally(err -> {
        System.err.println("No ACK: " + err.getMessage());
        return null;
    });
```

If `ackEnabled()` returns `false` (default), `publish(...)` completes immediately after sending.

### Timeouts and Errors

- If no ACK is received within the configured timeout, the returned `CompletionStage` completes exceptionally with `NoAckException`.
- Configure timeout via either:
  - System property: `redisbridge.ack.timeout.seconds`
  - Environment variable: `REDISBRIDGE_ACK_TIMEOUT_SECONDS`

Example (Java):

```java
System.setProperty("redisbridge.ack.timeout.seconds", "2");
```

Notes:

- ACK only confirms that the message reached the receiver’s Redis subscription; it does not indicate business-level processing success. Use the request-response pattern if you need a business response.
- If you publish to a channel with no active receivers, an `ackEnabled()` message will time out with `NoAckException`.

## Core Concepts

### BaseMessage

All messages must implement the `BaseMessage` interface and provide a namespace identifier.

### MessageRegistry

The registry manages message handler registrations:

- `register(namespace, messageClass)` - For messages without responses
- `register(namespace, messageClass, responseClass)` - For request-response pattern

### RedisRouter

The router handles message publishing and routing:

- `publish(baseMessage, entity)` - Fire-and-forget message broadcasting
- `waitResponse(baseMessage, entity, responseClass)` - Async request-response
- `reply(message, response)` - Reply to a received message

## Architecture

```
┌─────────────────┐
│  First Service  │
│                 │
│ ┌─────────────┐ │
│ │ RedisBridge │ │
│ │   Client    │ │
│ └──────┬──────┘ │
└────────┼────────┘
         │
    ┌────▼────┐
    │  Redis  │
    │  Pub/Sub│
    └────┬────┘
         │
┌────────┼────────┐
│ ┌──────▼──────┐ │
│ │ RedisBridge │ │
│ │   Client    │ │
│ └─────────────┘ │
│                 │
│  Other Service  │
└─────────────────┘
```

## Best Practices

1. **Use Unique Namespaces** - Ensure each message type has a unique namespace
2. **Keep Messages Immutable** - Use records or final classes
3. **Handle Errors** - Always add exception handlers to CompletableFutures
4. **Clean Shutdown** - Call `client.shutdown()` before application exit
5. **Connection Pooling** - Use connection pooling in your RedisConnectionProvider

## License

This project is licensed under the MIT License.
