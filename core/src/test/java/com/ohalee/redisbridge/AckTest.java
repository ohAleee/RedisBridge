package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.MessageRouter;
import com.ohalee.redisbridge.api.messaging.ack.exception.NoAckException;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.models.AckEnabledMessage;
import com.ohalee.redisbridge.models.TestBaseMessage;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AckTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String serverID() {
                return "ack-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-ack-test");
            }

            @Override
            public MessageRouter.Settings routerSettings() {
                return new MessageRouter.Settings(false, -1, 3);
            }
        };

        client.load();

        client.getRegistry()
                .register(AckEnabledMessage.NAMESPACE, AckEnabledMessage.class)
                .build();

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
                .build();
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.unload();
        }
    }

    @Test
    @DisplayName("ACK: publish to self with ackEnabled=true should complete after ACK")
    void testAckSuccessOnSelf() throws Exception {
        AckEnabledMessage msg = new AckEnabledMessage("hello");
        CompletableFuture<?> fut = client.getRedisRouter()
                .publish(msg, client.platformEntity())
                .toCompletableFuture();

        assertDoesNotThrow(() -> fut.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("ACK: publish to unknown receiver with ackEnabled=true should timeout with NoAckException")
    void testAckTimeoutWhenNoReceiver() {
        AckEnabledMessage msg = new AckEnabledMessage("timeout");

        CompletableFuture<Message<AckEnabledMessage>> fut = client.getRedisRouter()
                .publish(msg, MessageEntity.of("non-existent-receiver"))
                .toCompletableFuture();

        ExecutionException ex = assertThrows(ExecutionException.class, () -> fut.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof NoAckException, "Expected cause to be NoAckException");
    }

    @Test
    @DisplayName("ACK: ackEnabled=false should not wait for ACK even if receiver is unknown")
    void testNoAckRequestedPublishesImmediately() throws Exception {
        TestBaseMessage msg = new TestBaseMessage("no-ack");

        CompletableFuture<?> fut = client.getRedisRouter()
                .publish(msg, MessageEntity.of("non-existent-receiver"))
                .toCompletableFuture();

        assertDoesNotThrow(() -> fut.get(2, TimeUnit.SECONDS));
    }
}
