package com.msvcchat.entity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "messages")
@Data
@NoArgsConstructor
//@Builder
public class ChatMessage {
    @Id
    private String id;
    private String roomId;       // room identificador (por ejemplo "user:{userId}:trainer:{trainerId}")
    private String fromId;       // id del emisor (user o trainer)
    private String fromRole;     // "USER" o "TRAINER"
    private String text;
    private Instant createdAt = Instant.now();
}
