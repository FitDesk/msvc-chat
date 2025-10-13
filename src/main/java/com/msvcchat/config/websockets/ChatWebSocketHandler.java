package com.msvcchat.config.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msvcchat.config.security.JwtService;
import com.msvcchat.dtos.CreateChatMessageDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.entity.ConversationDocument;
import com.msvcchat.service.Impl.ChatRoomManagerImpl;
import com.msvcchat.service.ChatService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatService chatService;
    private final ChatRoomManagerImpl roomManager;
    private final JwtService jwtService;
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


    private Sinks.Many<ChatMessage> sinkFor(String roomId) {
        return sinks.computeIfAbsent(roomId, rid -> Sinks.many().multicast().onBackpressureBuffer());
    }


    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);

        log.info("🔌 WebSocket: Intentando conectar a sala: {}", roomId);

        // ✅ NUEVO: Verificar que la conversación existe en MongoDB
        return mongoTemplate.findById(roomId, ConversationDocument.class)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("❌ Conversación no encontrada en MongoDB: {}", roomId);
                    return session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Conversación no encontrada"))
                            .then(Mono.empty());
                }))
                .flatMap(conversation -> {
                    log.info("✅ Conversación encontrada: {}, participantes: {}",
                            roomId, conversation.getParticipants());

                    String token = extractToken(session);

                    if (token == null) {
                        log.warn("❌ No se pudo obtener token JWT");
                        return session.close(CloseStatus.BAD_DATA.withReason("Token no encontrado"));
                    }
                    log.info("Token encontrado");
                    return jwtService.validateToken(token)
                            .flatMap(jwt -> {
                                String email = jwt.getClaimAsString("email");
                                String role = jwt.getClaimAsString("authorities");

                                if (email == null || role == null) {
                                    log.error("❌ Claims inválidos en JWT");
                                    return session.close(CloseStatus.BAD_DATA.withReason("Claims inválidos"));
                                }

                                // ✅ Verificar que el usuario es participante de la conversación
                                if (!conversation.getParticipants().contains(email)) {
                                    log.error("❌ Usuario {} no es participante de la conversación {}",
                                            email, roomId);
                                    return session.close(CloseStatus.NOT_ACCEPTABLE
                                            .withReason("No eres participante de esta conversación"));
                                }

                                log.info("✅ Usuario {} autorizado para la conversación {}", email, roomId);

                                // Asociar al usuario con el room
                                roomManager.addUserToRoom(roomId, email);

                                Sinks.Many<ChatMessage> sink = roomManager.sinkFor(roomId);

                                // Recuperar el historial del chat
                                Flux<WebSocketMessage> history = chatService.getHistory(roomId)
                                        .map(dto -> {
                                            try {
                                                return session.textMessage(objectMapper.writeValueAsString(dto));
                                            } catch (
                                                    Exception e) {
                                                log.error("Error serializando mensaje", e);
                                                return session.textMessage("{}");
                                            }
                                        });

                                // Configurar mensajes en tiempo real
                                Mono<Void> inbound = session.receive()
                                        .map(WebSocketMessage::getPayloadAsText)
                                        .flatMap(text -> {
                                            try {
                                                CreateChatMessageDto dto = objectMapper.readValue(text, CreateChatMessageDto.class);
                                                dto.setFromEmail(email);
                                                return chatService.saveMessage(roomId, dto);
                                            } catch (
                                                    Exception e) {
                                                log.error("Error procesando mensaje: {}", e.getMessage());
                                                return Mono.empty();
                                            }
                                        })
                                        .then();

                                Flux<WebSocketMessage> outbound = sink.asFlux()
                                        .map(chatMessage -> {
                                            try {
                                                return session.textMessage(objectMapper.writeValueAsString(chatMessage));
                                            } catch (
                                                    Exception e) {
                                                log.error("Error enviando mensaje", e);
                                                return session.textMessage("{}");
                                            }
                                        });

                                // Enviar historial seguido de mensajes en tiempo real
                                return session.send(history.concatWith(outbound)).and(inbound);
                            })
                            .onErrorResume(e -> {
                                log.error("❌ Error al validar token JWT: {}", e.getMessage());
                                return session.close(CloseStatus.BAD_DATA.withReason("Token inválido"));
                            });
                });
    }


    private String extractToken(WebSocketSession session) {
        var headers = session.getHandshakeInfo().getHeaders();

        // 1. Intentar desde header Authorization (enviado por el Gateway)
        List<String> authHeaders = headers.get(HttpHeaders.AUTHORIZATION);
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                log.debug("✅ Token encontrado en header Authorization");
                return authHeader.substring(7);
            }
            log.debug("✅ Token encontrado en header Authorization (sin Bearer)");
            return authHeader;
        }

        // 2. Intentar desde header custom del Gateway
        List<String> customHeaders = headers.get("X-Auth-Token");
        if (customHeaders != null && !customHeaders.isEmpty()) {
            log.debug("✅ Token encontrado en header X-Auth-Token");
            return customHeaders.get(0);
        }

        // 3. Fallback: Intentar desde cookies (por si acaso)
        var cookies = session.getHandshakeInfo().getCookies();
        var accessTokenCookie = cookies.getFirst("access_token");
        if (accessTokenCookie != null) {
            log.debug("✅ Token encontrado en cookie 'access_token'");
            return accessTokenCookie.getValue();
        }

        log.warn("⚠️ No se encontró token en ninguna fuente");
        log.debug("Headers disponibles: {}", headers.keySet());
        log.debug("Cookies disponibles: {}", cookies.keySet());

        return null;
    }

    @PostConstruct
    void startChangeStreamListener() {
        mongoTemplate.changeStream(ChatMessage.class)
                .listen()
                .mapNotNull(ChangeStreamEvent::getBody)
                .distinct(ChatMessage::getId)
                .subscribe(msg -> {
                    if (msg != null && msg.getRoomId() != null) {
                        Sinks.Many<ChatMessage> sink = sinkFor(msg.getRoomId());
                        sink.tryEmitNext(msg);
                    }
                }, err -> log.error("Error en ChangeStream: {}", err.getMessage()));
    }

}