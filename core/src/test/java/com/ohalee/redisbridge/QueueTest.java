package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.MessageRouter;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.models.AckEnabledMessage;
import com.ohalee.redisbridge.models.TestBaseMessage;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QueueTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String serverID() {
                return "queue-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-queue-test");
            }

            @Override
            public MessageRouter.Settings routerSettings() {
                return new MessageRouter.Settings(true, 50, 2);
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
    @DisplayName("Should publish queued message successfully")
    void testPublishQueuedMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
                .onReceive(fullMessage -> latch.countDown())
                .build();

        TestBaseMessage message = new TestBaseMessage("queued test");
        client.getRedisRouter().publishQueued(message, client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received via queue");
    }

    @Test
    @DisplayName("Should publish multiple queued messages successfully")
    void testPublishMultipleQueuedMessages() throws InterruptedException {
        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger receivedCount = new AtomicInteger(0);

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
                .onReceive(fullMessage -> {
                    receivedCount.incrementAndGet();
                    latch.countDown();
                })
                .build();

        for (int i = 0; i < messageCount; i++) {
            client.getRedisRouter().publishQueued(new TestBaseMessage("queued test " + i), client.platformEntity());
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All messages should be received via queue");
        assertEquals(messageCount, receivedCount.get());
    }

    @Test
    @DisplayName("Should complete future when queued message is sent")
    void testQueuedMessageFutureCompletion() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        client.getRegistry()
                .register(TestBaseMessage.NAMESPACE, TestBaseMessage.class)
                .onReceive(fullMessage -> latch.countDown())
                .build();

        TestBaseMessage message = new TestBaseMessage("future test");
        CompletableFuture<Message<TestBaseMessage>> future = client.getRedisRouter()
                .publishQueued(message, client.platformEntity())
                .toCompletableFuture();

        Message<TestBaseMessage> sentMessage = future.get(5, TimeUnit.SECONDS);
        assertNotNull(sentMessage);
        assertEquals("future test", sentMessage.message().content());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish queued message with ACK successfully")
    void testPublishQueuedMessageWithAck() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        client.getRegistry()
                .register(AckEnabledMessage.NAMESPACE, AckEnabledMessage.class)
                .onReceive(fullMessage -> latch.countDown())
                .build();

        AckEnabledMessage message = new AckEnabledMessage("queued ack test");
        CompletableFuture<Message<AckEnabledMessage>> future = client.getRedisRouter()
                .publishQueued(message, client.platformEntity())
                .toCompletableFuture();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received via queue");
        Message<AckEnabledMessage> sentMessage = future.get(5, TimeUnit.SECONDS);
        assertNotNull(sentMessage);
        assertEquals("queued ack test", sentMessage.message().payload());
    }
}
