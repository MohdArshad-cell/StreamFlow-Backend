package com.streamflow.core.controller;

import com.streamflow.core.dto.NotificationRequest;
import com.streamflow.core.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notify")
public class NotificationController {

    @Autowired
    private NotificationService service;

    // Write (Slow path: async via Kafka)
    @PostMapping
    public ResponseEntity<String> trigger(@RequestBody NotificationRequest request) {
        String response = service.sendNotification(request.getMessage());
        return ResponseEntity.ok(response);
    }

    // Read (Fast path: sync via Redis)
    @GetMapping("/recent")
    public ResponseEntity<List<String>> getRecent() {
        return ResponseEntity.ok(service.getRecentNotifications());
    }
}