package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.MessageChannels;
import com.ohalee.redisbridge.api.messaging.MessageEntity;
import com.ohalee.redisbridge.api.messaging.Sender;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the per-project channel namespace. No Redis connection is required: clients are only
 * built here, never initialized.
 */
public class MessageChannelsTest {

    @Test
    @DisplayName("Should prefix every channel with the project prefix")
    void testChannelFormats() {
        MessageChannels channels = MessageChannels.withPrefix("my-project");

        assertEquals("my-project", channels.prefix());
        assertEquals("my-project:broadcast", channels.broadcast().channel());
        assertEquals("my-project:updates:broadcast", channels.broadcast("Updates").channel());
        assertEquals("my-project:target:server-1", channels.of("Server-1").channel());
        assertEquals("my-project:response:server-1", channels.response("Server-1").channel());
        assertEquals("my-project:ack:server-1", channels.ack("Server-1").channel());
    }

    @Test
    @DisplayName("Should resolve sender based channels through the sender id")
    void testSenderChannels() {
        MessageChannels channels = MessageChannels.withPrefix("my-project");
        Sender sender = Sender.from("Server-1", channels.of("Server-1"));

        assertEquals("my-project:response:server-1", channels.response(sender).channel());
        assertEquals("my-project:ack:server-1", channels.ack(sender).channel());
    }

    @Test
    @DisplayName("Should normalize the prefix and reject blank ones")
    void testPrefixValidation() {
        assertEquals("my-project", MessageChannels.withPrefix("  my-project:  ").prefix());
        assertThrows(IllegalArgumentException.class, () -> MessageChannels.withPrefix(""));
        assertThrows(IllegalArgumentException.class, () -> MessageChannels.withPrefix("   "));
        assertThrows(IllegalArgumentException.class, () -> MessageChannels.withPrefix(":"));
        assertThrows(IllegalArgumentException.class, () -> MessageChannels.withPrefix(null));
    }

    @Test
    @DisplayName("Should keep the JVM wide default in sync with MessageEntity")
    void testDefaultNamespace() {
        assertEquals(MessageEntity.PREFIX, MessageChannels.defaults().prefix());
        assertEquals(MessageEntity.broadcast().channel(), MessageChannels.defaults().broadcast().channel());
        assertEquals(MessageEntity.of("server-1").channel(), MessageChannels.defaults().of("server-1").channel());
        assertEquals(MessageEntity.response("server-1").channel(), MessageChannels.defaults().response("server-1").channel());
        assertEquals(MessageEntity.ack("server-1").channel(), MessageChannels.defaults().ack("server-1").channel());
    }

    @Test
    @DisplayName("Should use the JVM wide default when a client does not configure a prefix")
    void testClientDefaultsToJvmNamespace() {
        RedisBridgeClient client = new RedisBridgeClient() {
            @Override
            public String clientId() {
                return "default-client";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-channels-test");
            }
        };

        assertEquals(MessageChannels.defaults(), client.channels());
        assertEquals(MessageEntity.PREFIX + ":target:default-client", client.platformEntity().channel());
    }

    @Test
    @DisplayName("Should isolate clients configured with different prefixes")
    void testClientPrefixIsolation() {
        RedisBridgeClient first = RedisBridgeClient.builder()
                .clientId("service-1")
                .channelPrefix("project-a")
                .redisConnector(new TestRedisClient("redis-bridge-channels-a"))
                .build();

        RedisBridgeClient second = RedisBridgeClient.builder()
                .clientId("service-1")
                .channels(MessageChannels.withPrefix("project-b"))
                .redisConnector(new TestRedisClient("redis-bridge-channels-b"))
                .build();

        assertEquals("project-a:target:service-1", first.platformEntity().channel());
        assertEquals("project-b:target:service-1", second.platformEntity().channel());
        assertNotEquals(first.channels().broadcast().channel(), second.channels().broadcast().channel());
    }

    @Test
    @DisplayName("Should let a subclass override its namespace")
    void testOverriddenChannels() {
        RedisBridgeClient client = new RedisBridgeClient() {
            @Override
            public String clientId() {
                return "service-1";
            }

            @Override
            public MessageChannels channels() {
                return MessageChannels.withPrefix("my-project");
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-channels-override");
            }
        };

        assertEquals("my-project:target:service-1", client.platformEntity().channel());
    }
}
