package com.ohalee.redisbridge.client.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class CaffeineFactory {

    private static final ForkJoinPool loaderPool = new ForkJoinPool();

    private CaffeineFactory() {
    }

    public static Caffeine<@NotNull Object, @NotNull Object> newBuilder() {
        return Caffeine.newBuilder().executor(loaderPool);
    }

    public static Executor executor() {
        return loaderPool;
    }

}