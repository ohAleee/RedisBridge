package com.ohalee.redisbridge;

import com.ohalee.redisbridge.models.TestBaseMessage;
import com.ohalee.redisbridge.models.UserLoginBaseMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Test
    @DisplayName("Should create TestBaseMessage with correct values")
    void testCreateTestBaseMessage() {
        String content = "test content";
        TestBaseMessage message = new TestBaseMessage(content);

        assertEquals(content, message.content());
        assertEquals(TestBaseMessage.NAMESPACE, message.namespace());
    }

    @Test
    @DisplayName("Should create UserLoginBaseMessage with correct values")
    void testCreateUserLoginBaseMessage() {
        String username = "testUser";
        long timestamp = System.currentTimeMillis();
        UserLoginBaseMessage message = new UserLoginBaseMessage(username, timestamp);

        assertEquals(username, message.username());
        assertEquals(timestamp, message.timestamp());
        assertEquals(UserLoginBaseMessage.NAMESPACE, message.namespace());
    }

    @Test
    @DisplayName("TestBaseMessage should be immutable")
    void testMessageImmutability() {
        TestBaseMessage message1 = new TestBaseMessage("content");
        TestBaseMessage message2 = new TestBaseMessage("content");

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotSame(message1, message2);
    }

    @Test
    @DisplayName("Messages with different content should not be equal")
    void testMessageInequality() {
        TestBaseMessage message1 = new TestBaseMessage("content1");
        TestBaseMessage message2 = new TestBaseMessage("content2");

        assertNotEquals(message1, message2);
    }
}
