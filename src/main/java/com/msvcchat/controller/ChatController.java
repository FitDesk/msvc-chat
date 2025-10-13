package com.msvcchat.controller;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.members.MemberDto;
import com.msvcchat.dtos.security.RoleDto;
import com.msvcchat.dtos.security.SimpleRoleDto;
import com.msvcchat.dtos.security.SimpleUserDto;
import com.msvcchat.dtos.security.UserSecurityDto;
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
    public Flux<UserDto> getUsersByRole(
            @PathVariable String role,
            Authentication authentication) {

        String normalizedRole = role.toUpperCase();
        log.info("Obteniendo usuarios con rol: {}", normalizedRole);

        return securityWebClient.get()
                .uri("/users/by-role/{role}", normalizedRole)
                .retrieve()
                .bodyToFlux(SimpleUserDto.class)
                .flatMap(userSec -> {
                    log.info("Usuario traído de msvc-security: {}", userSec);

                    return membersWebClient.get()
                            .uri("/public/member/{userId}", userSec.id())
                            .retrieve()
                            .bodyToMono(MemberDto.class)
                            .map(member -> {
                                log.info("Datos de miembro: {}", member);

                                // ✅ Construir el nombre completo
                                String displayName = buildDisplayName(
                                        member.firstName(),
                                        member.lastName(),
                                        userSec.email()
                                );

                                String mainRole = userSec.roles().stream()
                                        .findFirst()
                                        .map(SimpleRoleDto::name)
                                        .orElse("USER");

                                return new UserDto(
                                        userSec.id(),
                                        displayName,
                                        mainRole,
                                        member.profileImageUrl()
                                );
                            })
                            .onErrorResume(error -> {
                                log.warn("⚠️ No se pudo obtener datos de members para userId={}: {}",
                                        userSec.id(), error.getMessage());

                                String fallbackName = buildDisplayName(
                                        userSec.firstName(),
                                        userSec.lastName(),
                                        userSec.email()
                                );

                                return Mono.just(new UserDto(
                                        userSec.id(),
                                        fallbackName,
                                        userSec.roles().stream()
                                                .findFirst()
                                                .map(SimpleRoleDto::name)
                                                .orElse("USER"),
                                        null
                                ));
                            });
                })
                .doOnError(error -> log.error("❌ Error obteniendo usuarios por rol {}: {}",
                        normalizedRole, error.getMessage()));
    }

    private String buildDisplayName(String firstName, String lastName, String email) {
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

    private UUID getUserIdFromToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getClaim("user_id");
            return UUID.fromString(userId);
        }
        throw new IllegalStateException("No se pudo obtener el user_id del token");
    }
}