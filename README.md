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
- ⚡ **Async by Design** - Non-blocking CompletableFuture-based responses with native Java 9+ timeout handling
- 🧵 **Virtual Threads** - Built for modern Java, utilizing virtual threads for high concurrency
- 🎯 **Namespace-based Routing** - Organized message handling with namespaces
- 🧩 **Type Adapters** - Custom serialization and deserialization for complex types
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

Messages are simple records implementing `Message`:

```java

@MessageName("user:login")
public record UserLoginMessage(String username, long timestamp) implements Message {
}
```

### 2. Define Responses (Optional)

For request-response pattern, create response records implementing `Response`:

```java
public record LoginResponse(String token, boolean success) implements Response {
}
```

### 3. Create a Client

```java
RedisBridgeClient client = new RedisBridgeClient() {
    @Override
    public String clientId() {
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
client.getMessageRegistry().register(UserLoginMessage.class)
    .onReceive(fullMessage -> {
        UserLoginMessage msg = fullMessage.message();
        System.out.println("User logged in: " + msg.username());
    })
    .build();
```

#### Request-Response Pattern

```java
client.getMessageRegistry().register(UserLoginMessage.class, LoginResponse.class)
    .onReceive(fullMessage -> {
        // Handle incoming request and send response
        LoginResponse response = new LoginResponse("token-123", true);
        client.getRedisRouter().reply(fullMessage, response);
    })
    .onResponse(fullResponse -> {
        // Handle responses to our requests
        System.out.println("Login response: " + fullResponse.token());
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

#### Broadcast to Channels

You can now broadcast messages to specific custom channels using `MessageEntity.broadcast(name)`. This is useful for
grouping services (e.g., "chat", "lobby").

```java
client.getRedisRouter().publish(
    new MyGlobalAnnouncement("Maintenance in 5m"),
    MessageEntity.broadcast("updates")
);
```

Subscribe the broadcast channel:

```java
redisClient.getRedisListener().subscribe(MessageEntity.broadcast("updates"));
```

#### Wait for Response

```java
client.getRedisRouter().waitResponse(
        new UserLoginMessage("john_doe", System.currentTimeMillis()),
        receiver.platformEntity()
).thenAccept(fullResponse -> {
    LoginResponse response = fullResponse.response();
    System.out.println("Received: " + response.token());
})
.exceptionally(throwable -> {
    System.err.println("Error: "+throwable.getMessage());
    return null;
});
```

#### Wait for Multiple Responses

When broadcasting a message to multiple clients, you can wait for a variable number of responses. The router will track how many clients received the broadcast and dynamically set the expected response count.

```java
client.getRedisRouter().waitResponses(
        new UserLoginMessage("john_doe", System.currentTimeMillis()),
        MessageEntity.broadcast("updates"),
        true // includeSender: whether to include the local sender in the expected response count
).thenAccept(fullResponses -> {
    System.out.println("Received " + fullResponses.size() + " responses!");
    for (PacketResponse<UserLoginMessage, LoginResponse> fullResponse : fullResponses) {
        System.out.println("Response: " + fullResponse.response().token());
    }
})
.exceptionally(throwable -> {
    System.err.println("Error: " + throwable.getMessage());
    return null;
});
```

## Interceptors

You can register interceptors to hook into the message lifecycle (before sending and after receiving). This is useful
for logging, tracing (e.g., OpenTelemetry), or modifying message content globally.

```java
client.addInterceptor(new MessageInterceptor() {
    @Override
    public <M extends Message> Packet<M> onSend(Packet<M> packet) {
        System.out.println("Sending message: " + packet.uniqueId());
        return packet;
    }

    @Override
    public <M extends Message> Packet<M> onReceive(Packet<M> packet) {
        System.out.println("Received message: " + packet.uniqueId());
        return packet;
    }
});
```

## ACK (Acknowledgements)

RedisBridge can optionally wait for a lightweight delivery acknowledgement (ACK) when publishing a message. This helps
ensure that the target service (or channel) actually received the message.

### Enable ACK on a Message

ACK is opt-in and controlled per-message by overriding `ackEnabled()` in your `Message` implementation:

```java
@MessageName("events:critical")
public record CriticalEvent(String id) implements Message {

    @Override
    public boolean ackEnabled() { return true; }
}
```

When such a message is published, the sender will wait for an ACK from the receiver. The receiver sends ACK
automatically upon receipt — no extra code is needed in your handler.

### Publishing with ACK

```java
// Will wait for ACK because CriticalEvent.ackEnabled() == true
client.getRedisRouter().publish(new CriticalEvent("42"), receiver.platformEntity())
        .thenRun(() -> System.out.println("ACK received"))
        .exceptionally(err ->{
            System.err.println("No ACK: "+err.getMessage());
            return null;
        });
```

If `ackEnabled()` returns `false` (default), `publish(...)` completes immediately after sending.

### Timeouts and Errors

- If no ACK is received within the configured timeout, the returned `CompletionStage` completes exceptionally with
  `NoAckException`.
- Configure timeout and other settings by overriding `routerSettings()` in your `RedisBridgeClient`:

```java
RedisBridgeClient client = new RedisBridgeClient() {
    // ... other overrides

    @Override
    public MessageRouter.Settings routerSettings() {
        return new MessageRouter.Settings(
            true, // activeQueueExecutor
            100,  // queuePublishDelayMillis
            2,    // ackTimeoutSeconds
            15    // responseTimeoutSeconds
        );
    }
};
```

Notes:

- ACK only confirms that the message reached the receiver’s Redis subscription; it does not indicate business-level
  processing success. Use the request-response pattern if you need a business response.
- If you publish to a channel with no active receivers, an `ackEnabled()` message will time out with `NoAckException`.

## Type Adapters

RedisBridge uses **Gson** under the hood. You can register custom adapters (`TypeAdapter`, `JsonSerializer`, `JsonDeserializer`) to handle complex objects or specific serialization logic.

### 1. Create an Adapter

```java
public class ComponentAdapter implements JsonSerializer<Component>, JsonDeserializer<Component> {

    @Override
    public JsonElement serialize(Component src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(JSONComponentSerializer.json().serialize(src));
    }

    @Override
    public Component deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        return JSONComponentSerializer.json().deserialize(json.getAsString());
    }
}
```

### 2. Register the Adapter

Register your adapters when building the client using the `Builder`:

```java
RedisBridgeClient client = RedisBridgeClient.builder()
    .clientId("my-service-1")
    .redisConnector(new MyRedisConnectionProvider())
    .registerAdapter(Component.class, new ComponentAdapter())
    .build();
```

### 3. Field-Specific Adapters

Since RedisBridge relies on Gson, you can directly use Gson's `@JsonAdapter` on specific fields:

```java
public record PlayerData(
    String name,
    @JsonAdapter(ComponentAdapter.class) Component component
) implements Message {
    // ...
}
```

## Core Concepts

### Message

All messages must implement the `Message` interface and provide a namespace identifier (explicitly or via class name).

### MessageRegistry

The registry manages message handler registrations:

- `register(messageClass)` - For messages without responses
- `register(messageClass, responseClass)` - For request-response pattern

### MessageRouter

The router handles message publishing and routing:

- `publish(message, entity)` - Fire-and-forget message broadcasting
- `waitResponse(message, entity, responseClass)` - Async request-response
- `reply(packet, response)` - Reply to a received message

### BaseRedisClient

To simplify creating your `RedisConnectionProvider`, you can extend `BaseRedisClient`, which manages the connection pool and pub/sub connection for you:

```java
public class MyRedisClient extends BaseRedisClient {
    public MyRedisClient() {
        this.pool = // initialize your ObjectPool<StatefulRedisConnection<String, String>>
        this.pubSubConnection = // initialize your pub/sub connection
    }
}
```

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
## Acknowledgments

[![YourKit Logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

This project uses YourKit tools for profiling and performance optimization. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).