package com.msvcchat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDocument {
    @Id
    private String id;
    private Set<String> participants;
    private String lastMessageId;
//    private Boolean isFavorite;
    private Instant lastActivity;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
