package com.streamflow.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "logs")
@Data
@NoArgsConstructor
public class NotificationLog {
    @Id
    private String id;
    private String message;
    private LocalDateTime timestamp;
}