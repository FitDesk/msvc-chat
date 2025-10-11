package com.msvcchat.dtos;

public record CreateConversationDto(
        String participantId,
        String participantRole
) {
}
