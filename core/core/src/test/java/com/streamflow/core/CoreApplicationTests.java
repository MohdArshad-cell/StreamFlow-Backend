package com.streamflow.core;

import com.streamflow.core.model.NotificationLog;
import com.streamflow.core.repository.NotificationRepository;
import com.streamflow.core.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers // 1. Tells JUnit to manage Docker containers
class CoreApplicationTests {

    // 2. Spin up a real MongoDB (ver 6.0)
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    // 3. Spin up a real Kafka (Confluent 7.4)
    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    // 4. Inject the Container URLs into Spring Boot Properties
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationRepository repository;

    @Test
    void testEndToEndNotificationFlow() {
        // GIVEN
        String message = "Integration Test Message " + System.currentTimeMillis();

        // WHEN: We send a message
        service.sendNotification(message);

        // THEN: We wait (max 10s) for Kafka to consume it and save to Mongo
        await().pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<NotificationLog> logs = repository.findAll();
                    assertEquals(1, logs.size(), "Should have 1 log entry");
                    assertEquals(message, logs.get(0).getMessage(), "Message content should match");
                });
    }
}