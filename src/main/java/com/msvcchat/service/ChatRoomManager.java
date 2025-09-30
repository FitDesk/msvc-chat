package com.msvcchat.service;

import com.msvcchat.entity.ChatMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatRoomManager {

    private final ReactiveMongoTemplate mongoTemplate;
    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    public Sinks.Many<ChatMessage> sinkFor(String roomId) {
        return sinks.computeIfAbsent(roomId, rid -> Sinks.many().multicast().onBackpressureBuffer());
    }


    public void addUserToRoom(String roomId, String userId) {
        roomUsers.computeIfAbsent(roomId, rid -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public Set<String> getUsersInRoom(String roomId) {
        return roomUsers.getOrDefault(roomId, Set.of());
    }



    public void broadcast(ChatMessage msg) {
        if (msg == null || msg.getRoomId() == null)
            return;
        Sinks.Many<ChatMessage> s = sinks.get(msg.getRoomId());
        if (s != null) {
            s.tryEmitNext(msg);
        }
    }


    @PostConstruct
    void startChangeStreamListener() {
        //Va a estar esuchando inserts o updates en la colencion de ChatMessage
        mongoTemplate.changeStream(ChatMessage.class)
                .listen()
                .mapNotNull(ChangeStreamEvent::getBody)
                .subscribe(msg -> {
                    if (msg != null && msg.getRoomId() != null) {
                        Sinks.Many<ChatMessage> s = sinks.get(msg.getRoomId());
                        if (s != null)
                            s.tryEmitNext(msg);
                    }
                }, Throwable::printStackTrace);
    }


}
