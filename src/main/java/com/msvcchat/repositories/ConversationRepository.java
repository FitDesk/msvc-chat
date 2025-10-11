package com.msvcchat.repositories;

import com.msvcchat.entity.ConversationDocument;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface ConversationRepository extends ReactiveMongoRepository<ConversationDocument, String> {
    Flux<ConversationDocument> findByParticipantsContaining(String userEmail);
    @Query("{'participants': {$all: ?0}}")
    Mono<ConversationDocument> findByParticipantsContainingAll(Set<String> participants);
}
