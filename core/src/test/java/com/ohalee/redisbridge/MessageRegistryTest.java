package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.models.TestMessage;
import com.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageRegistryTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String clientId() {
                return "registry-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-registry-test");
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
    @DisplayName("Should create registration builder for message without response")
    void testCreateSimpleRegistrationBuilder() {
        MessageRegistry.RegistrationBuilder<TestMessage> builder = client.getMessageRegistry().register(TestMessage.class);

        assertNotNull(builder);
    }

    @Test
    @DisplayName("Should build simple registration successfully")
    void testBuildSimpleRegistration() {
        String namespace = "registry:simple";

        assertDoesNotThrow(() -> {
            client.getMessageRegistry().register(namespace, TestMessage.class)
                    .onReceive(msg -> {
                    })
                    .build();
        });

        assertTrue(client.getMessageRegistry().isRegistered(namespace));
        assertNotNull(client.getMessageRegistry().getRegistration(namespace));
    }

    @Test
    @DisplayName("Should check if namespace is registered")
    void testIsRegistered() {
        String registeredNamespace = "registry:registered";
        String unregisteredNamespace = "registry:unregistered";

        client.getMessageRegistry().register(registeredNamespace, TestMessage.class)
                .onReceive(msg -> {
                })
                .build();

        assertTrue(client.getMessageRegistry().isRegistered(registeredNamespace));
        assertFalse(client.getMessageRegistry().isRegistered(unregisteredNamespace));
    }
}
