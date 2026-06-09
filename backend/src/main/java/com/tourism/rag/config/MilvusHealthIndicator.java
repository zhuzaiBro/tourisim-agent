package com.tourism.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Actuator 健康检查：Milvus 不可达时报告 DOWN。
 */
@Component
public class MilvusHealthIndicator implements HealthIndicator {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Override
    public Health health() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(milvusHost, milvusPort), 3000);
            return Health.up()
                    .withDetail("host", milvusHost)
                    .withDetail("port", milvusPort)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("host", milvusHost)
                    .withDetail("port", milvusPort)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
