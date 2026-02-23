package com.ohalee.redisbridge;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.ohalee.redisbridge.api.messaging.request.Message;
import com.ohalee.redisbridge.api.messaging.request.MessageName;
import com.ohalee.redisbridge.client.RedisBridgeClient;
import com.ohalee.redisbridge.redis.TestRedisClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CustomAdapterTest {

    private RedisBridgeClient client;

    @BeforeAll
    void setUp() {
        client = RedisBridgeClient.builder()
                .clientId("custom-adapter-test")
                .redisConnector(new TestRedisClient("redis-bridge-custom-adapter-test"))
                .registerAdapter(CustomObject.class, new CustomObjectAdapter())
                .build();

        client.load();
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.unload();
        }
    }

    @Test
    @DisplayName("Should use custom TypeAdapter registered via Builder")
    void testCustomAdapterRegistration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CustomObject> receivedObject = new AtomicReference<>();

        client.getMessageRegistry()
                .register(MessageWithCustomObject.class)
                .onReceive(fullMessage -> {
                    receivedObject.set(fullMessage.message().getCustomObject());
                    latch.countDown();
                })
                .build();

        CustomObject obj = new CustomObject("hello world");
        MessageWithCustomObject msg = new MessageWithCustomObject("content", obj);

        client.getRedisRouter().publish(msg, client.platformEntity());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
        assertEquals("hello world", receivedObject.get().getValue(), "Custom object value should match");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CustomObject {
        private String value;
    }

    public static class CustomObjectAdapter extends TypeAdapter<CustomObject> {
        @Override
        public void write(JsonWriter out, CustomObject value) throws IOException {
            out.value("CUSTOM:" + value.getValue());
        }

        @Override
        public CustomObject read(JsonReader in) throws IOException {
            String val = in.nextString();
            if (val.startsWith("CUSTOM:")) {
                return new CustomObject(val.substring(7));
            }
            return new CustomObject(val);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @MessageName("test:custom-object")
    public static class MessageWithCustomObject implements Message {
        private String content;
        private CustomObject customObject;

    }
}
