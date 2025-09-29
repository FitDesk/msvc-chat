package com.msvcchat.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class ChatMessageDto {
    private String id;
    private String roomId;
    private String fromId;
    private String fromRole;
    private String text;
    private Instant createdAt;
}
