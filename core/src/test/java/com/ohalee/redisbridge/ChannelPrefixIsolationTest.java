package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.response.PacketResponse;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.client.messaging.request.MessageRegistryImpl;
import com.ohalee.redisbridge.models.TestMessage;
import com.ohalee.redisbridge.models.TestResponse;
import com.ohalee.redisbridge.models.UserLoginMessage;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that clients configured with different channel prefixes never see each other's
 * traffic, even when they share a client id, and that request/reply stays inside the prefix.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChannelPrefixIsolationTest {

    private RedisBridgeClient a1;
    private RedisBridgeClient a2;
    private RedisBridgeClient b1;

    private final AtomicInteger bReceived = new AtomicInteger();

    @BeforeAll
    void setUp() {
        a1 = RedisBridgeClient.builder()
                .clientId("srv-1")
                .channelPrefix("proj-a")
                .messageRegistry(new MessageRegistryImpl())
                .redisConnector(new TestRedisClient("e2e-a1"))
                .build();
        a2 = RedisBridgeClient.builder()
                .clientId("srv-2")
                .channelPrefix("proj-a")
                .messageRegistry(new MessageRegistryImpl())
                .redisConnector(new TestRedisClient("e2e-a2"))
                .build();
        b1 = RedisBridgeClient.builder()
                .clientId("srv-1")
                .channelPrefix("proj-b")
                .messageRegistry(new MessageRegistryImpl())
                .redisConnector(new TestRedisClient("e2e-b1"))
                .build();

        a1.initialize();
        a2.initialize();
        b1.initialize();

        a1.getRedisListener().subscribe(a1.channels().broadcast("news"));
        b1.getRedisListener().subscribe(b1.channels().broadcast("news"));

        a1.load();
        a2.load();
        b1.load();

        a1.getMessageRegistry().register(TestMessage.class, TestResponse.class)
                .onReceive(packet -> a1.getRedisRouter().reply(packet, new TestResponse("from-a1")))
                .build();
        a2.getMessageRegistry().register(TestMessage.class, TestResponse.class)
                .onResponse(Assertions::assertNotNull)
                .build();
        b1.getMessageRegistry().register(TestMessage.class, TestResponse.class)
                .onReceive(packet -> bReceived.incrementAndGet())
                .build();
    }

    @AfterAll
    void tearDown() {
        a1.unload();
        a2.unload();
        b1.unload();
    }

    @Test
    @DisplayName("request/reply works inside a custom prefix and never leaks to another one")
    void testRequestReply() throws Exception {
        PacketResponse<TestMessage, TestResponse> response = a2.getRedisRouter()
                .<TestMessage, TestResponse>waitResponse(new TestMessage("ping"), a2.channels().of("srv-1"))
                .get(10, TimeUnit.SECONDS);

        assertEquals("from-a1", response.response().response());
        Thread.sleep(300);
        assertEquals(0, bReceived.get(), "proj-b client must not receive proj-a traffic");
    }

    @Test
    @DisplayName("broadcast stays inside the prefix")
    void testBroadcastIsolation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        a1.getMessageRegistry().register(UserLoginMessage.class)
                .onReceive(packet -> latch.countDown())
                .build();
        b1.getMessageRegistry().register(UserLoginMessage.class)
                .onReceive(packet -> bReceived.incrementAndGet())
                .build();

        a2.getMessageRegistry().register(UserLoginMessage.class).build();
        a2.getRedisRouter().publish(new UserLoginMessage("john", 1L),
                a2.channels().broadcast("news"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "proj-a broadcast should reach the proj-a subscriber");
        Thread.sleep(300);
        assertEquals(0, bReceived.get(), "proj-b client must not receive proj-a broadcasts");
    }
}
