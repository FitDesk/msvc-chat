package com.msvcchat.controller;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.members.MemberDto;
import com.msvcchat.service.ChatService;
import com.msvcchat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    @Qualifier("securityWebClient")
    private final WebClient securityWebClient;
    @Qualifier("membersWebClient")
    private final WebClient membersWebClient;


    @GetMapping("/conversations")
    public Flux<ConversationDto> getConversations(Authentication authentication) {
        String userEmail = authentication.getName();
        return conversationService.getConversationsByUser(userEmail);
    }

    @PostMapping("/conversations")
    public Mono<ConversationDto> createConversation(
            @RequestBody CreateConversationDto dto,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return conversationService.createConversation(userEmail, dto);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Flux<ChatMessageDto> getMessages(
            @PathVariable String conversationId,
            Authentication authentication) {
        return chatService.getHistory(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public Mono<ChatMessageDto> sendMessage(
            @PathVariable String conversationId,
            @RequestBody CreateChatMessageDto dto,
            Authentication authentication) {

        String userEmail = authentication.getName();
        UUID userId = getUserIdFromToken(authentication);

        dto.setFromEmail(userEmail);
        dto.setFromId(userId.toString());

        return chatService.saveMessage(conversationId, dto);
    }

    @PatchMapping("/conversations/{conversationId}/read")
    public Mono<Void> markAsRead(
            @PathVariable String conversationId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return conversationService.markAsRead(conversationId, userEmail);
    }


    @GetMapping("/users/{role}")
    public Flux<UserConnectionDto> getUsersByRole(
            @PathVariable String role,
            Authentication authentication) {

        String normalizedRole = role.toUpperCase();
        log.info("Obteniendo usuarios con rol: {}", normalizedRole);

        return conversationService.getAllUsersByRole(normalizedRole);
    }


    private UUID getUserIdFromToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getClaim("user_id");
            return UUID.fromString(userId);
        }
        throw new IllegalStateException("No se pudo obtener el user_id del token");
    }
}