package com.msvcchat.service;

import com.msvcchat.dtos.ChatMessageDto;
import com.msvcchat.dtos.CreateChatMessageDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatService {
    Mono<ChatMessageDto> saveMessage(String roomId, CreateChatMessageDto dto);

    Flux<ChatMessageDto> getHistory(String roomId);

}
