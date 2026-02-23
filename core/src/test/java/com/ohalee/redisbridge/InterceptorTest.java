package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.interceptor.MessageInterceptor;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.Packet;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.models.TestMessage;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ohalee.redisbridge.client.RedisBridgeClient.MESSAGE_REGISTRY;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InterceptorTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String clientId() {
                return "interceptor-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-interceptor-test");
            }
        };
        client.load();
    }

    @AfterAll
    void tearDown() {
        client.unload();
    }

    @Test
    @DisplayName("Should call onSend and onReceive interceptors")
    void testInterceptors() throws InterruptedException {
        AtomicBoolean sendCalled = new AtomicBoolean(false);
        AtomicBoolean receiveCalled = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        client.addInterceptor(new MessageInterceptor() {
            @Override
            public <M extends Message> @NonNull Packet<M> onSend(@NonNull Packet<M> packet) {
                sendCalled.set(true);
                return packet;
            }

            @Override
            public <M extends Message> @NonNull Packet<M> onReceive(@NonNull Packet<M> packet) {
                receiveCalled.set(true);
                return packet;
            }
        });

        MESSAGE_REGISTRY
                .register(TestMessage.class)
                .onReceive(msg -> latch.countDown())
                .build();

        client.getRedisRouter().publish(new TestMessage("hello"), client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
        assertTrue(sendCalled.get(), "onSend should have been called");
        assertTrue(receiveCalled.get(), "onReceive should have been called");
    }
}
