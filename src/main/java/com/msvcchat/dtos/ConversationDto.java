package com.msvcchat.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {
    private String id;
    private UserDto participant;
    private ChatMessageDto lastMessage;
    private Instant lastMessageDate;
    private int unreadCount;
    private Boolean isFavorite;
    private Boolean isConnected;
    private Instant createdAt;
    private Instant updatedAt;
}
