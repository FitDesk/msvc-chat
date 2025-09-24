package com.msvcchat.config.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msvcchat.dtos.ChatMessageDto;
import com.msvcchat.dtos.CreateChatMessageDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.mappers.ChatMessageMapper;
import com.msvcchat.repositories.ChatMessageRepository;
import com.msvcchat.service.ChatRoomManager;
import com.msvcchat.service.ChatService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
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
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatService chatService;
    private final ChatMessageRepository repo;
    private final ChatRoomManager roomManager;
    private final ChatMessageMapper mapper;
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
                        log.info("**** ENVIANDO MENSAJE {} ****", text);
                        log.info("***** CHAT DTO ****");
                        log.info(createDto.toString());
                        Mono<Void> response = chatService.saveMessage(roomId, createDto).then();
                        log.info("***** RESPONSE ****");
                        log.info(response.toString());
                        return response;
                    } catch (
                            Exception e) {
                        return Mono.empty();
                    }
                })
                .then();

        Flux<WebSocketMessage> outbound = sink.asFlux()
                .distinct(ChatMessage::getId)
                .map(m -> {
                    try {
                        ChatMessageDto dto = mapper.toDto(m);
                        dto.setId(m.getId());
                        log.info("****** CHAT MESSAGE DTO  {} ", dto);
                        return objectMapper.writeValueAsString(dto);
                    } catch (
                            Exception e) {
                        e.printStackTrace();
                        log.error("*************ERRROR *********");
                        log.error(e.getMessage());
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

        return session.send(history.concatWith(outbound)).and(inbound);
    }


    @PostConstruct
    void startChangeStreamListener() {
        mongoTemplate.changeStream(ChatMessage.class)
                .listen()
                .mapNotNull(ChangeStreamEvent::getBody)
                .distinct(ChatMessage::getId) // Evita procesar mensajes duplicados
                .subscribe(msg -> {
                    if (msg != null && msg.getRoomId() != null) {
                        Sinks.Many<ChatMessage> s = sinks.get(msg.getRoomId());
                        if (s != null) {
                            s.tryEmitNext(msg);
                        }
                    }
                }, err -> {
                    log.error("Error en ChangeStream: {}", err.getMessage(), err);
                });
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
