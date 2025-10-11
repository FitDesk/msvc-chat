package com.msvcchat.controller;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.security.RoleDto;
import com.msvcchat.dtos.security.UserSecurityDto;
import com.msvcchat.service.ChatService;
import com.msvcchat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WebClient webClient;

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

    /**
     * Obtener usuarios disponibles para chatear (filtrados por rol)
     */
    @GetMapping("/users/{role}")
    public Flux<UserDto> getUsersByRole(
            @PathVariable String role,
            Authentication authentication) {

        String normalizedRole = role.toUpperCase();
        log.info("📋 Obteniendo usuarios con rol: {}", normalizedRole);

        return webClient.get()
                .uri("/users/by-role/{role}", normalizedRole)
                .retrieve()
                .bodyToFlux(UserSecurityDto.class)
                .map(userSec -> {
                    // ✅ Construir el nombre de forma segura
                    String displayName = buildDisplayName(userSec);

                    log.debug("Usuario mapeado: id={}, name={}, email={}",
                            userSec.getId(), displayName, userSec.getEmail());

                    return new UserDto(
                            userSec.getId().toString(),
                            displayName,
                            userSec.getRoles().stream()
                                    .findFirst()
                                    .map(RoleDto::getName)
                                    .orElse("USER"),
                            null // avatar
                    );
                })
                .doOnError(error -> log.error("❌ Error obteniendo usuarios por rol {}: {}",
                        normalizedRole, error.getMessage()));
    }

    private String buildDisplayName(UserSecurityDto user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = user.getEmail();

        // Caso 1: Ambos nombres disponibles
        if (firstName != null && !firstName.isBlank() &&
                lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }

        // Caso 2: Solo firstName
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }

        // Caso 3: Solo lastName
        if (lastName != null && !lastName.isBlank()) {
            return lastName;
        }

        // Caso 4: Usar email (parte antes de @)
        if (email != null && !email.isBlank()) {
            return email.split("@")[0];
        }

        // Caso 5: Fallback
        return "Usuario sin nombre";
    }

    /**
     * Extraer el userId del token JWT
     */
    private UUID getUserIdFromToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getClaim("user_id");
            return UUID.fromString(userId);
        }
        throw new IllegalStateException("No se pudo obtener el user_id del token");
    }
}