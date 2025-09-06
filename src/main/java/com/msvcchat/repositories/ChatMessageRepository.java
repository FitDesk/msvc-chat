package com.msvcchat.repositories;

import com.msvcchat.entity.ChatMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
    Flux<ChatMessage> findByRoomIdOrderByCreatedAtAsc(String roomId);
}
