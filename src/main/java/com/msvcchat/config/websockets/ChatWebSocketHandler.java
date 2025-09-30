package com.msvcchat.config.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msvcchat.config.security.JwtService;
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
import org.springframework.web.reactive.socket.CloseStatus;
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
    private final ChatRoomManager roomManager;
    private final JwtService jwtService;
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


    private Sinks.Many<ChatMessage> sinkFor(String roomId) {
        return sinks.computeIfAbsent(roomId, rid -> Sinks.many().multicast().onBackpressureBuffer());
    }

//    @Override
//    public Mono<Void> handle(WebSocketSession session) {
//        String path = session.getHandshakeInfo().getUri().getPath();
//        String roomId = path.substring(path.lastIndexOf('/') + 1);
//
//        String token = session.getHandshakeInfo().getHeaders().getFirst("Authorization");
//        if (token == null || !token.startsWith("Bearer ")) {
//            return session.close(CloseStatus.BAD_DATA);
//        }
//
//
//
//        Sinks.Many<ChatMessage> sink = roomManager.sinkFor(roomId);
//
//        Mono<Void> inbound = session.receive()
//                .map(WebSocketMessage::getPayloadAsText)
//                .flatMap(text -> {
//                    try {
//                        CreateChatMessageDto createDto = objectMapper.readValue(text, CreateChatMessageDto.class);
//                        log.info("**** ENVIANDO MENSAJE {} ****", text);
//                        log.info("***** CHAT DTO ****");
//                        log.info(createDto.toString());
//                        Mono<Void> response = chatService.saveMessage(roomId, createDto).then();
//                        log.info("***** RESPONSE ****");
//                        log.info(response.toString());
//                        return response;
//                    } catch (
//                            Exception e) {
//                        return Mono.empty();
//                    }
//                })
//                .then();
//
//        Flux<WebSocketMessage> outbound = sink.asFlux()
//                .distinct(ChatMessage::getId)
//                .map(m -> {
//                    try {
//                        ChatMessageDto dto = mapper.toDto(m);
//                        dto.setId(m.getId());
//                        log.info("****** CHAT MESSAGE DTO  {} ", dto);
//                        return objectMapper.writeValueAsString(dto);
//                    } catch (
//                            Exception e) {
//                        e.printStackTrace();
//                        log.error("*************ERRROR *********");
//                        log.error(e.getMessage());
//                        return "{}";
//                    }
//                })
//                .map(session::textMessage);
//
//        Flux<WebSocketMessage> history = chatService.getHistory(roomId)
//                .map(dto -> {
//                    try {
//                        return objectMapper.writeValueAsString(dto);
//                    } catch (
//                            Exception e) {
//                        return "{}";
//                    }
//                })
//                .map(session::textMessage);
//
//        return session.send(history.concatWith(outbound)).and(inbound);
//    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);

        String token = session.getHandshakeInfo().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return session.close(CloseStatus.BAD_DATA);
        }
        token = token.substring(7);

        return jwtService.validateToken(token)
                .flatMap(jwt -> {
                    String email = jwt.getClaimAsString("email");
                    String role = jwt.getClaimAsString("authorities");

                    if (email == null || role == null) {
                        return session.close(CloseStatus.BAD_DATA);
                    }

                    // Asociar al usuario con el room
                    roomManager.addUserToRoom(roomId, email);

                    Sinks.Many<ChatMessage> sink = roomManager.sinkFor(roomId);

                    // Recuperar el historial del chat
                    Flux<WebSocketMessage> history = chatService.getHistory(roomId)
                            .map(dto -> {
                                try {
                                    return objectMapper.writeValueAsString(dto);
                                } catch (
                                        Exception e) {
                                    log.error("Error serializando historial de mensajes: {}", e.getMessage());
                                    return "{}";
                                }
                            })
                            .map(session::textMessage);

                    // Configurar mensajes en tiempo real
                    Mono<Void> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .flatMap(text -> {
                                try {
                                    CreateChatMessageDto createDto = objectMapper.readValue(text, CreateChatMessageDto.class);
                                    createDto.setFromEmail(email);
                                    createDto.setFromRole(role);
                                    return chatService.saveMessage(roomId, createDto).then();
                                } catch (
                                        Exception e) {
                                    log.error("Error procesando mensaje entrante: {}", e.getMessage());
                                    return Mono.empty();
                                }
                            })
                            .then();

                    Flux<WebSocketMessage> outbound = sink.asFlux()
                            .map(chatMessage -> {
                                try {
                                    String jsonMessage = objectMapper.writeValueAsString(chatMessage);
                                    return session.textMessage(jsonMessage);
                                } catch (
                                        Exception e) {
                                    log.error("Error serializando mensaje: {}", e.getMessage());
                                    return session.textMessage("{}");
                                }
                            });

                    // Enviar historial seguido de mensajes en tiempo real
                    return session.send(history.concatWith(outbound)).and(inbound);
                })
                .onErrorResume(e -> {
                    log.error("Error al validar el token JWT: {}", e.getMessage());
                    return session.close(CloseStatus.BAD_DATA);
                });
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
