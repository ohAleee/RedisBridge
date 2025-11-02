package it.ohalee.redisbridge;

import it.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import it.ohalee.redisbridge.api.redis.RedisConnectionProvider;
import it.ohalee.redisbridge.client.RedisBridgeClient;
import it.ohalee.redisbridge.models.TestBaseMessage;
import it.ohalee.redisbridge.redis.TestRedisClient;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageRegistryTest {

    private RedisBridgeClient client;
    private MessageRegistry registry;

    @BeforeAll
    void setUp() {
        client = new RedisBridgeClient() {
            @Override
            public String serverID() {
                return "registry-test";
            }

            @Override
            protected RedisConnectionProvider provideRedisConnector() {
                return new TestRedisClient("redis-bridge-registry-test");
            }
        };

        client.load();
        registry = client.getRegistry();
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
        MessageRegistry.RegistrationBuilder<TestBaseMessage> builder =
                registry.register(TestBaseMessage.NAMESPACE, TestBaseMessage.class);

        assertNotNull(builder);
    }

    @Test
    @DisplayName("Should build simple registration successfully")
    void testBuildSimpleRegistration() {
        String namespace = "registry:simple";

        assertDoesNotThrow(() -> {
            registry.register(namespace, TestBaseMessage.class)
                    .onReceive(msg -> {})
                    .build();
        });

        assertTrue(registry.isRegistered(namespace));
        assertNotNull(registry.getRegistration(namespace));
    }

    @Test
    @DisplayName("Should check if namespace is registered")
    void testIsRegistered() {
        String registeredNamespace = "registry:registered";
        String unregisteredNamespace = "registry:unregistered";

        registry.register(registeredNamespace, TestBaseMessage.class)
                .onReceive(msg -> {})
                .build();

        assertTrue(registry.isRegistered(registeredNamespace));
        assertFalse(registry.isRegistered(unregisteredNamespace));
    }
}
