package com.ohalee.redisbridge;

import com.ohalee.redisbridge.api.messaging.request.MessageRegistry;
import com.ohalee.redisbridge.models.TestMessage;
import com.ohalee.redisbridge.models.UserLoginMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Test
    @DisplayName("Should create TestMessage with correct values")
    void testCreateTestBaseMessage() {
        String content = "test content";
        TestMessage message = new TestMessage(content);

        assertEquals(content, message.content());
        assertEquals(TestMessage.NAMESPACE, MessageRegistry.getNamespace(message));
    }

    @Test
    @DisplayName("Should create UserLoginMessage with correct values")
    void testCreateUserLoginBaseMessage() {
        String username = "testUser";
        long timestamp = System.currentTimeMillis();
        UserLoginMessage message = new UserLoginMessage(username, timestamp);

        assertEquals(username, message.username());
        assertEquals(timestamp, message.timestamp());
        assertEquals(UserLoginMessage.NAMESPACE, MessageRegistry.getNamespace(message));
    }

    @Test
    @DisplayName("TestMessage should be immutable")
    void testMessageImmutability() {
        TestMessage message1 = new TestMessage("content");
        TestMessage message2 = new TestMessage("content");

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotSame(message1, message2);
    }

    @Test
    @DisplayName("Messages with different content should not be equal")
    void testMessageInequality() {
        TestMessage message1 = new TestMessage("content1");
        TestMessage message2 = new TestMessage("content2");

        assertNotEquals(message1, message2);
    }
}
