package com.msvcchat.service;

import com.msvcchat.dtos.ConversationDto;
import com.msvcchat.dtos.CreateConversationDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationService {
    Flux<ConversationDto> getConversationsByUser(String userEmail);
    Mono<ConversationDto> createConversation(String userEmail, CreateConversationDto dto);
    Mono<Void> markAsRead(String conversationId,String userEmail);
    Mono<String> getOrCreateRoomId(String user1Email,String user2Email);
}
