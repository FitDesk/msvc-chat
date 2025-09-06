package com.msvcchat.config.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.repositories.ChatMessageRepository;
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

    private final ChatMessageRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final ReactiveMongoTemplate mongoTemplate;

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
        Sinks.Many<ChatMessage> sink = sinkFor(roomId);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> {
                    try {
                        ChatMessage msg = mapper.readValue(text, ChatMessage.class);
                        msg.setRoomId(roomId);
                        return repo.save(msg)
                                .doOnNext(saved -> sink.tryEmitNext(saved));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .then();

        Flux<WebSocketMessage> outbound = sink.asFlux()
                .map(m -> {
                    try { return mapper.writeValueAsString(m); } catch (Exception e) { return "{}";}
                })
                .map(session::textMessage);

        // cuando cliente se conecta, opcional: enviar historial reciente
        Flux<WebSocketMessage> history = repo.findByRoomIdOrderByCreatedAtAsc(roomId)
                .map(m -> {
                    try { return mapper.writeValueAsString(m); } catch (Exception e) { return "{}"; }
                })
                .map(session::textMessage);

        return session.send(Flux.concat(history, outbound)).and(inbound);
    }

    /**
     * Change Stream listener: cuando hay inserts en collection "messages",
     * emitimos a los sinks locales (para propagar mensajes entre instancias).
     * Requiere replica set.
     */
    private void startChangeStreamListener() {
        // escucha sin filtro (puedes filtrar por ns/collection o por roomId)
        mongoTemplate.changeStream(ChatMessage.class)
                .listen() // devuelve Flux<ChangeStreamEvent<ChatMessage>>
                .map(event -> event.getBody()) // ChatMessage
                .subscribe(msg -> {
                    if (msg != null && msg.getRoomId() != null) {
                        Sinks.Many<ChatMessage> s = sinks.get(msg.getRoomId());
                        if (s != null) {
                            s.tryEmitNext(msg);
                        }
                    }
                }, err -> {
                    // en prod haz reintentos/monitorización
                    err.printStackTrace();
                });
    }
}
