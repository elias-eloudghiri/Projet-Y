package com.example.back;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String S3_ACCESS_KEY = "test-access-key";
    static final String S3_SECRET_KEY = "test-secret-key";
    private static final int S3_PORT = 8333;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    /** Same S3 server as docker-compose; credentials are those of its admin identity. */
    @Bean
    GenericContainer<?> seaweedfs() {
        return new GenericContainer<>("chrislusf/seaweedfs:4.47")
                .withCommand("server", "-s3", "-dir=/data")
                .withEnv("AWS_ACCESS_KEY_ID", S3_ACCESS_KEY)
                .withEnv("AWS_SECRET_ACCESS_KEY", S3_SECRET_KEY)
                .withExposedPorts(S3_PORT)
                // Anonymous requests are denied once the S3 API is up.
                .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfs) {
        return registry -> {
            registry.add(
                    "app.storage.endpoint",
                    () -> "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT));
            registry.add("app.storage.access-key", () -> S3_ACCESS_KEY);
            registry.add("app.storage.secret-key", () -> S3_SECRET_KEY);
        };
    }

    /** Replaces the application clock wherever a {@link java.time.Clock} is injected. */
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
