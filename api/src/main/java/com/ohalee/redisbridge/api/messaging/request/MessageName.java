package com.ohalee.redisbridge.api.messaging.request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define a custom namespace for a message.
 * If not present, the simple class name will be used as the namespace.
 * The namespace must be unique across all message types to avoid conflicts.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MessageName {
    String value();
}
