package com.ohalee.redisbridge.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StaticCredentialsProvider;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.support.ConnectionPoolSupport;
import com.ohalee.redisbridge.client.redis.BaseRedisClient;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

public class TestRedisClient extends BaseRedisClient {

    protected RedisClient client;

    private final String host;
    private final int port;
    private final String password;
    private final String clientName;

    public TestRedisClient(String clientName) {
        this("localhost", 6379, null, clientName);
    }

    public TestRedisClient(String host, int port, String password, String clientName) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.clientName = clientName;
    }

    @Override
    public void connect() {
        RedisURI uri = RedisURI.create(host, port);
        if (password != null && !password.isEmpty())
            uri.setCredentialsProvider(new StaticCredentialsProvider(RedisCredentials.just(null, password)));
        uri.setClientName(clientName);
        uri.setTimeout(Duration.ofSeconds(5));

        this.client = RedisClient.create(uri);

        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(30);
        poolConfig.setMinIdle(10);
        poolConfig.setMaxWait(Duration.ofSeconds(2));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        this.pool = ConnectionPoolSupport.createGenericObjectPool(client::connect, poolConfig);

        this.pubSubConnection = this.client.connectPubSub(StringCodec.UTF8);
    }

    @Override
    public void disconnect() {
        super.disconnect();
        this.client.shutdown();
    }
}
