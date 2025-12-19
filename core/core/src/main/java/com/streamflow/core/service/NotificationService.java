package com.streamflow.core.service;

import com.streamflow.core.model.NotificationLog;
import com.streamflow.core.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String TOPIC = "user-notifications";
    private static final String DLQ_TOPIC = "notifications-dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate; // <--- ADDED: Redis Client

    // --- 1. PRODUCER ---
    public String sendNotification(String message) {
        kafkaTemplate.send(TOPIC, message);
        log.info("📤 Message sent to Kafka topic: {}", message);
        return "Notification queued successfully";
    }

    // --- 2. CONSUMER (Resilient Worker) ---
    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Retryable(
        retryFor = RuntimeException.class, 
        maxAttempts = 3, 
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void consume(String message) {
        log.info("🔄 Processing Attempt: {}", message);

        if (message.contains("error")) {
            throw new RuntimeException("Simulated API Failure!");
        }

        // Step A: Persistent Storage (MongoDB) - The "Truth"
        NotificationLog entity = new NotificationLog();
        entity.setMessage(message);
        entity.setTimestamp(LocalDateTime.now());
        repository.save(entity);
        log.info("✅ Saved to MongoDB!");

        // Step B: Performance Cache (Redis) - The "Speed"
        // We push to the head of the list
        redisTemplate.opsForList().leftPush("recent_notifications", message);
        // We trim the list to ensure we only keep the latest 10 items (Automatic housekeeping)
        redisTemplate.opsForList().trim("recent_notifications", 0, 9);
        log.info("⚡ Cached in Redis!");
    }

    // --- 3. FALLBACK (Recover Method) ---
    @Recover
    public void recover(RuntimeException e, String message) {
        log.error("❌ All Retries Failed. Moving to DLQ...");
        kafkaTemplate.send(DLQ_TOPIC, "FAILED: " + message);
    }

    // --- 4. DLQ LISTENER ---
    @KafkaListener(topics = DLQ_TOPIC, groupId = "dlq-group")
    public void consumeDLQ(String message) {
        log.warn("⚠️ DLQ Received Bad Message: {}", message);
    }

    // --- 5. READ FROM CACHE (Low Latency) ---
    // Fetch the latest notifications directly from RAM (Redis)
    public List<String> getRecentNotifications() {
        // range(0, -1) means "get all items in the list"
        return redisTemplate.opsForList().range("recent_notifications", 0, -1);
    }
}