package com.msvcchat.service.Impl;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.members.MemberDto;
import com.msvcchat.dtos.security.RoleDto;
import com.msvcchat.dtos.security.SimpleRoleDto;
import com.msvcchat.dtos.security.SimpleUserDto;
import com.msvcchat.dtos.security.UserSecurityDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.entity.ConversationDocument;
import com.msvcchat.mappers.ConversationMapper;
import com.msvcchat.repositories.ConversationRepository;
import com.msvcchat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ConversationMapper conversationMapper;
    @Qualifier("securityWebClient")
    private final WebClient securityWebClient;

    @Qualifier("membersWebClient")
    // ✅ NUEVO
    private final WebClient membersWebClient;


    @Override
    public Flux<ConversationDto> getConversationsByUser(String userEmail) {
        return conversationRepository.findByParticipantsContaining(userEmail)
                .flatMap(conv -> enrichConversationDto(conv, userEmail));
    }

    @Override
    public Mono<ConversationDto> createConversation(String userEmail, CreateConversationDto dto) {
        // Obtener información del usuario participante desde msvc-security
        return getUserByIdFromSecurity(dto.participantId())
                .flatMap(participantUser -> {
                    String participantEmail = participantUser.getEmail();

                    // Verificar si ya existe conversación
                    return conversationRepository.findByParticipantsContainingAll(Set.of(userEmail, participantEmail))
                            .switchIfEmpty(
                                    // Crear nueva conversación
                                    conversationRepository.save(new ConversationDocument(
                                            null,
                                            Set.of(userEmail, participantEmail),
                                            null,
                                            Instant.now(),
                                            Instant.now(),
                                            Instant.now()
                                    ))
                            )
                            .flatMap(conv -> enrichConversationDto(conv, userEmail));
                });
    }

    @Override
    public Mono<Void> markAsRead(String conversationId, String userEmail) {
        // TODO: Implementar lógica de mensajes no leídos con una colección adicional
        // Por ahora retornamos vacío
        return Mono.empty();
    }

    @Override
    public Mono<String> getOrCreateRoomId(String user1Email, String user2Email) {
        Set<String> participants = Set.of(user1Email, user2Email);
        return conversationRepository.findByParticipantsContainingAll(participants)
                .switchIfEmpty(
                        conversationRepository.save(new ConversationDocument(
                                null,
                                participants,
                                null,
                                Instant.now(),
                                Instant.now(),
                                Instant.now()
                        ))
                )
                .map(ConversationDocument::getId);
    }

    /**
     * Enriquecer el DTO de conversación con información del participante y último mensaje
     */
    private Mono<ConversationDto> enrichConversationDto(ConversationDocument conversation, String currentUserEmail) {
        ConversationDto dto = conversationMapper.toDto(conversation);

        String participantEmail = conversation.getParticipants().stream()
                .filter(email -> !email.equals(currentUserEmail))
                .findFirst()
                .orElse(null);

        if (participantEmail == null) {
            log.warn("⚠️ No se encontró email del participante en conversación {}", conversation.getId());
            return Mono.just(dto);
        }

        // 1️⃣ Obtener datos de security
        return getUserByEmailFromSecurity(participantEmail)
                .flatMap(userSecurityDto -> {
                    String userId = userSecurityDto.id();

                    // 2️⃣ Obtener datos de members
                    return getMemberFromMembers(userId)
                            .map(memberDto -> {
                                // ✅ Combinar datos de ambos microservicios
                                String displayName = buildDisplayName(
                                        memberDto.firstName(),
                                        memberDto.lastName(),
                                        userSecurityDto.email()
                                );

                                String mainRole = userSecurityDto.roles().stream()
                                        .findFirst()
                                        .map(SimpleRoleDto::name)
                                        .orElse("USER");

                                UserDto enrichedUser = new UserDto(
                                        userId,
                                        displayName,
                                        mainRole,
                                        memberDto.profileImageUrl() // ✅ Foto de perfil
                                );

                                dto.setParticipant(enrichedUser);
                                return dto;
                            })
                            .onErrorResume(error -> {
                                // ✅ Si falla members, usar solo datos de security
                                log.warn("⚠️ No se pudo obtener datos de members para userId={}: {}",
                                        userId, error.getMessage());

                                String fallbackName = buildDisplayName(
                                        userSecurityDto.firstName(),
                                        userSecurityDto.lastName(),
                                        userSecurityDto.email()
                                );

                                UserDto partialUser = new UserDto(
                                        userId,
                                        fallbackName,
                                        userSecurityDto.roles().stream()
                                                .findFirst()
                                                .map(SimpleRoleDto::name)
                                                .orElse("USER"),
                                        null // Sin foto
                                );

                                dto.setParticipant(partialUser);
                                return Mono.just(dto);
                            });
                })
                .flatMap(enrichedDto -> {
                    // 3️⃣ Obtener último mensaje
                    if (conversation.getLastMessageId() != null) {
                        return mongoTemplate.findById(conversation.getLastMessageId(), ChatMessage.class)
                                .map(lastMsg -> {
                                    ChatMessageDto msgDto = new ChatMessageDto();
                                    msgDto.setId(lastMsg.getId());
                                    msgDto.setText(lastMsg.getText());
                                    msgDto.setCreatedAt(lastMsg.getCreatedAt());
                                    enrichedDto.setLastMessage(msgDto);
                                    return enrichedDto;
                                })
                                .defaultIfEmpty(enrichedDto);
                    }
                    return Mono.just(enrichedDto);
                })
                .onErrorResume(error -> {
                    log.error("❌ Error enriqueciendo conversación {}: {}",
                            conversation.getId(), error.getMessage());
                    return Mono.just(dto);
                });
    }

    private String buildDisplayName(String firstName, String lastName, String email) {
        if (firstName != null && !firstName.isBlank() &&
                lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }

        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }

        if (lastName != null && !lastName.isBlank()) {
            return lastName;
        }

        if (email != null && !email.isBlank()) {
            return email.split("@")[0];
        }

        return "Usuario sin nombre";
    }

    /**
     * Obtener usuario por email desde msvc-security usando WebClient
     */
    private Mono<SimpleUserDto> getUserByEmailFromSecurity(String email) {
        return securityWebClient.get()
                .uri("/users/by-email/{email}", email)
                .retrieve()
                .bodyToMono(SimpleUserDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo usuario de security: {}", error.getMessage()));
    }

    private Mono<MemberDto> getMemberFromMembers(String userId) {
        return membersWebClient.get()
                .uri("/public/member/{userId}", userId)
                .retrieve()
                .bodyToMono(MemberDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo miembro de members: {}", error.getMessage()));
    }


    private Mono<UserSecurityDto> getUserByIdFromSecurity(String userId) {
        return securityWebClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserSecurityDto.class)
                .doOnError(error -> log.error("Error obteniendo usuario por ID {}: {}", userId, error.getMessage()));
    }
}