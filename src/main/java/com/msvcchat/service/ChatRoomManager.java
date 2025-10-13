package com.msvcchat.service;

import com.msvcchat.entity.ChatMessage;
import reactor.core.publisher.Sinks;

import java.util.Set;

public interface ChatRoomManager {
    Sinks.Many<ChatMessage> sinkFor(String roomId);
    void addUserToRoom(String roomId, String userId);
    Set<String> getUsersInRoom(String roomId);
    void broadcast(ChatMessage msg);
}
