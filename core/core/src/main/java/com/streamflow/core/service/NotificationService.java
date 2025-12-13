package com.streamflow.core.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final String TOPIC = "user-notifications";
    private static final String DLQ_TOPIC = "notifications-dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationRepository repository;

    // --- 1. PRODUCER (The Sender) ---
    // Sends a message to the main Kafka topic.
    public String sendNotification(String message) {
        kafkaTemplate.send(TOPIC, message);
        return "Message sent to Kafka topic: " + message;
    }

    // --- 2. CONSUMER (The Worker) ---
    // Reads from Kafka and saves to MongoDB.
    // Has "Fault Tolerance": If it crashes, it moves data to DLQ.
    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    public void consume(String message) {
        System.out.println("🔥 Kafka Listener received: " + message);

        try {
            // SIMULATE ERROR: If message contains "error", force a crash.
            if (message.contains("error")) {
                throw new RuntimeException("Simulated API Failure!");
            }

            // Happy Path: Save to MongoDB
            NotificationLog log = new NotificationLog();
            log.setMessage(message);
            log.setTimestamp(LocalDateTime.now());
            repository.save(log);
            System.out.println("✅ Saved to MongoDB!");

        } catch (Exception e) {
            System.err.println("❌ Processing Failed. Moving to DLQ...");
            // Send to Dead Letter Topic manually so data isn't lost
            kafkaTemplate.send(DLQ_TOPIC, "FAILED: " + message);
        }
    }

    // --- 3. DLQ LISTENER (The Safety Net) ---
    // Listens to the 'dead' messages.
    // In a real company, this would trigger a Slack alert or PagerDuty.
    @KafkaListener(topics = DLQ_TOPIC, groupId = "dlq-group")
    public void consumeDLQ(String message) {
        System.out.println("⚠️ DLQ Received Bad Message: " + message);
    }
}

// --- SUPPORTING CLASSES (Keep these here for simplicity) ---

@RestController
@RequestMapping("/api/notify")
class NotificationController {
    @Autowired
    private NotificationService service;

    @PostMapping
    public String trigger(@RequestParam String message) {
        return service.sendNotification(message);
    }
}

@Document(collection = "logs")
@Data
@NoArgsConstructor
class NotificationLog {
    @Id
    private String id;
    private String message;
    private LocalDateTime timestamp;
}

interface NotificationRepository extends MongoRepository<NotificationLog, String> {}