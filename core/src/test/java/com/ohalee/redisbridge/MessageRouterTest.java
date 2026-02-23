package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.models.TestMessage;
import com.ohalee.redisbridge.models.TestResponse;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageRouterTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String clientId() {
                return "router-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-router-test");
            }
        };

        client.initialize();

        client.getRedisListener().subscribe(MessageEntity.broadcast("test"));

        client.load();
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.unload();
        }
    }

    @Test
    @DisplayName("Should publish message successfully")
    void testPublishMessage() {
        TestMessage message = new TestMessage("test content");

        if (!client.getMessageRegistry().isRegistered(TestMessage.NAMESPACE)) {
            client.getMessageRegistry()
                    .register(TestMessage.class)
                    .build();
        }

        assertDoesNotThrow(() -> {
            client.getRedisRouter().publish(message, client.platformEntity());
        });
    }

    @Test
    @DisplayName("Should route published message to handler")
    void testMessageRouting() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedContent = new AtomicReference<>();

        client.getMessageRegistry()
                .register(TestMessage.class)
                .onReceive(fullMessage -> {
                    receivedContent.set(fullMessage.message().content());
                    latch.countDown();
                })
                .build();

        TestMessage message = new TestMessage("routing test");
        client.getRedisRouter().publish(message, client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("routing test", receivedContent.get());
    }

    @Test
    @DisplayName("Should wait for response and receive it")
    void testWaitForResponse() throws InterruptedException, ExecutionException, TimeoutException {
        client.getMessageRegistry()
                .register(TestMessage.class, TestResponse.class)
                .onReceive(fullMessage -> {
                    TestResponse response = new TestResponse("Response to: " + fullMessage.message().content());
                    client.getRedisRouter().reply(fullMessage, response);
                })
                .build();

        CompletableFuture<PacketResponse<TestMessage, TestResponse>> future = client.getRedisRouter()
                .waitResponse(
                        new TestMessage("request"),
                        client.platformEntity()
                );

        var result = future.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should wait for multiple responses and receive them")
    void testWaitForMultipleResponses() throws InterruptedException, ExecutionException, TimeoutException {
        client.getMessageRegistry()
                .register(TestMessage.class, TestResponse.class)
                .onReceive(fullMessage -> {
                    TestResponse response = new TestResponse("Multi-response to: " + fullMessage.message().content());

                    client.getRedisRouter().reply(fullMessage, response);
                })
                .build();

        CompletableFuture<java.util.List<PacketResponse<TestMessage, TestResponse>>> future = client.getRedisRouter()
                .waitResponses(
                        new TestMessage("multi-request"),
                        MessageEntity.broadcast("test"),
                        true
                );

        var result = future.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Multi-response to: multi-request", result.getFirst().response().response());
    }
}
