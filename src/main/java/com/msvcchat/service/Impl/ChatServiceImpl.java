package com.msvcchat.service.Impl;

import com.msvcchat.dtos.ChatMessageDto;
import com.msvcchat.dtos.CreateChatMessageDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.mappers.ChatMessageMapper;
import com.msvcchat.repositories.ChatMessageRepository;
import com.msvcchat.repositories.ConversationRepository;
import com.msvcchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageMapper mapper;
    private final ChatRoomManagerImpl chatRoomManagerImpl;

    @Override
    public Mono<ChatMessageDto> saveMessage(String roomId, CreateChatMessageDto dto) {
        ChatMessage entity = mapper.toEntity(dto);
        entity.setRoomId(roomId);
        return chatMessageRepository
                .save(entity)
                .flatMap(savedMessage -> {
                    return conversationRepository.findById(roomId)
                            .flatMap(conversation -> {
                                conversation.setLastMessageId(savedMessage.getId());
                                conversation.setLastActivity(savedMessage.getCreatedAt());
                                return conversationRepository.save(conversation);
                            })
                            .onErrorResume(error -> {
                                log.warn("Error al actualizar la converzacion {} :{}", roomId, error.getMessage());
                                return Mono.empty();
                            }).thenReturn(savedMessage);
                }).doOnNext(chatRoomManagerImpl::broadcast)
                .map(mapper::toDto);
    }

    @Override
    public Flux<ChatMessageDto> getHistory(String roomId) {
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).map(mapper::toDto);
    }
}
