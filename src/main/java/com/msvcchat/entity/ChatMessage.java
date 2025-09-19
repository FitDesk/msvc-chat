package com.msvcchat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    private String id;
    private String roomId;
    private String fromId;
    private String fromRole;
    private String text;
    private Instant createdAt = Instant.now();
}
