package it.ohalee.redisbridge;

import it.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import it.ohalee.redisbridge.client.RedisBridgeClient;
import it.ohalee.redisbridge.models.TestBaseMessage;
import it.ohalee.redisbridge.models.TestResponse;
import it.ohalee.redisbridge.models.UserLoginBaseMessage;
import it.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisBridgeIntegrationTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String serverID() {
                return "test-server";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-test");
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
    @DisplayName("Should register message handler without response")
    void testSimpleMessageRegistration() {
        assertDoesNotThrow(() -> {
            client.getRegistry()
                    .register(UserLoginBaseMessage.NAMESPACE, UserLoginBaseMessage.class)
                    .onReceive(fullMessage -> {
                        assertNotNull(fullMessage);
                        assertNotNull(fullMessage.message());
                    })
                    .build();
        });

        assertTrue(client.getRegistry().isRegistered(UserLoginBaseMessage.NAMESPACE));
    }

    @Test
    @DisplayName("Should register message handler with response")
    void testMessageRegistrationWithResponse() {
        assertDoesNotThrow(() -> {
            client.getRegistry()
                    .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class, TestResponse.class)
                    .onReceive(Assertions::assertNotNull)
                    .onResponse(Assertions::assertNotNull)
                    .build();
        });

        assertTrue(client.getRegistry().isRegistered(TestBaseMessage.NAMESPACE));
    }

    @Test
    @DisplayName("Should publish and receive simple message")
    void testPublishAndReceiveMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedUsername = new AtomicReference<>();

        client.getRegistry()
                .register(UserLoginBaseMessage.NAMESPACE, UserLoginBaseMessage.class)
                .onReceive(fullMessage -> {
                    receivedUsername.set(fullMessage.message().username());
                    latch.countDown();
                })
                .build();

        UserLoginBaseMessage message = new UserLoginBaseMessage("testUser", System.currentTimeMillis());
        client.getRedisRouter().publish(message, client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received within 5 seconds");
        assertEquals("testUser", receivedUsername.get());
    }

    @Test
    @DisplayName("Should handle request-response pattern")
    void testRequestResponsePattern() throws InterruptedException {
        CountDownLatch requestLatch = new CountDownLatch(1);
        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<String> responseMessage = new AtomicReference<>();

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class, TestResponse.class)
                .onReceive(fullMessage -> {
                    requestLatch.countDown();
                    TestResponse response = new TestResponse("Echo: " + fullMessage.message().content());
                    client.getRedisRouter().reply(fullMessage, response);
                })
                .onResponse(fullResponse -> {
                    responseMessage.set(fullResponse.response().response());
                    responseLatch.countDown();
                })
                .build();

        client.getRedisRouter()
                .waitResponse(new TestBaseMessage("Hello"), client.platformEntity(), TestResponse.class)
                .thenAccept(fullResponse -> {
                    responseMessage.set(fullResponse.response().response());
                    responseLatch.countDown();
                });

        assertTrue(requestLatch.await(5, TimeUnit.SECONDS), "Request should be received");
        assertTrue(responseLatch.await(5, TimeUnit.SECONDS), "Response should be received");
        assertTrue(responseMessage.get().contains("Echo: Hello"));
    }
}