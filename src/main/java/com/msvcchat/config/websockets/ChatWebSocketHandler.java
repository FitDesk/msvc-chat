package com.msvcchat.config.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msvcchat.dtos.CreateChatMessageDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.mappers.ChatMessageMapper;
import com.msvcchat.repositories.ChatMessageRepository;
import com.msvcchat.service.ChatRoomManager;
import com.msvcchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatService chatService;
    private final ChatMessageRepository repo;
    private final ChatRoomManager roomManager;
    private final ChatMessageMapper mapper;
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

//    public ChatWebSocketHandler(ChatMessageRepository repo, ReactiveMongoTemplate mongoTemplate) {
//        this.repo = repo;
//        this.mongoTemplate = mongoTemplate;
//        startChangeStreamListener(); // inicializa escucha global
//    }

    private Sinks.Many<ChatMessage> sinkFor(String roomId) {
        return sinks.computeIfAbsent(roomId, rid -> Sinks.many().multicast().onBackpressureBuffer());
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);

        Sinks.Many<ChatMessage> sink = roomManager.sinkFor(roomId);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> {
                    try {
                        CreateChatMessageDto createDto = objectMapper.readValue(text, CreateChatMessageDto.class);
                        return chatService.saveMessage(roomId, createDto).then();
                    } catch (
                            Exception e) {
                        return Mono.empty();
                    }
                })
                .then();

        Flux<WebSocketMessage> outbound = sink.asFlux()
                .map(m -> {
                    try {
                        return objectMapper.writeValueAsString(mapper.toDto(m));
                    } catch (
                            Exception e) {
                        return "{}";
                    }
                })
                .map(session::textMessage);

        Flux<WebSocketMessage> history = chatService.getHistory(roomId)
                .map(dto -> {
                    try {
                        return objectMapper.writeValueAsString(dto);
                    } catch (
                            Exception e) {
                        return "{}";
                    }
                })
                .map(session::textMessage);

        return session.send(Flux.concat(history, outbound)).and(inbound);
    }
}

/**
 * Change Stream listener: cuando hay inserts en collection "messages",
 * emitimos a los sinks locales (para propagar mensajes entre instancias).
 * Requiere replica set.
 */
//private void startChangeStreamListener() {
//    // escucha sin filtro (puedes filtrar por ns/collection o por roomId)
//    mongoTemplate.changeStream(ChatMessage.class)
//            .listen() // devuelve Flux<ChangeStreamEvent<ChatMessage>>
//            .map(event -> event.getBody()) // ChatMessage
//            .subscribe(msg -> {
//                if (msg != null && msg.getRoomId() != null) {
//                    Sinks.Many<ChatMessage> s = sinks.get(msg.getRoomId());
//                    if (s != null) {
//                        s.tryEmitNext(msg);
//                    }
//                }
//            }, err -> {
//                // en prod haz reintentos/monitorización
//                err.printStackTrace();
//            });
//}
//}
