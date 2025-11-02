
package it.ohalee.redisbridge;

import it.ohalee.redisbridge.api.messaging.response.MessageResponse;
import it.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import it.ohalee.redisbridge.client.RedisBridgeClient;
import it.ohalee.redisbridge.models.TestBaseMessage;
import it.ohalee.redisbridge.models.TestResponse;
import it.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisRouterTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String serverID() {
                return "router-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-router-test");
            }
        };

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
        TestBaseMessage message = new TestBaseMessage("test content");

        if (!client.getRegistry().isRegistered(TestBaseMessage.NAMESPACE)) {
            client.getRegistry()
                    .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
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

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
                .onReceive(fullMessage -> {
                    receivedContent.set(fullMessage.message().content());
                    latch.countDown();
                })
                .build();

        TestBaseMessage message = new TestBaseMessage("routing test");
        client.getRedisRouter().publish(message, client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("routing test", receivedContent.get());
    }

    @Test
    @DisplayName("Should wait for response and receive it")
    void testWaitForResponse() throws InterruptedException, ExecutionException, TimeoutException {
        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class, TestResponse.class)
                .onReceive(fullMessage -> {
                    TestResponse response = new TestResponse("Response to: " + fullMessage.message().content());
                    client.getRedisRouter().reply(fullMessage, response);
                })
                .build();

        CompletableFuture<MessageResponse<TestBaseMessage, TestResponse>> future = client.getRedisRouter()
                .waitResponse(
                        new TestBaseMessage("request"),
                        client.platformEntity(),
                        TestResponse.class
                );

        var result = future.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
    }
}
