package com.msvcchat.service.Impl;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.security.RoleDto;
import com.msvcchat.dtos.security.UserSecurityDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.entity.ConversationDocument;
import com.msvcchat.mappers.ConversationMapper;
import com.msvcchat.repositories.ConversationRepository;
import com.msvcchat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WebClient webClient;

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
            return Mono.just(dto);
        }

        return getUserByEmailFromSecurity(participantEmail)
                .flatMap(userSecurityDto -> {
                    // ✅ Construir nombre de forma segura
                    String displayName = buildDisplayName(userSecurityDto);

                    UserDto userDto = new UserDto(
                            userSecurityDto.getId().toString(),
                            displayName,
                            userSecurityDto.getRoles().stream()
                                    .findFirst()
                                    .map(RoleDto::getName)
                                    .orElse("USER"),
                            null
                    );
                    dto.setParticipant(userDto);

                    // Obtener último mensaje si existe
                    if (conversation.getLastMessageId() != null) {
                        return mongoTemplate.findById(conversation.getLastMessageId(), ChatMessage.class)
                                .map(lastMsg -> {
                                    ChatMessageDto msgDto = new ChatMessageDto();
                                    msgDto.setId(lastMsg.getId());
                                    msgDto.setText(lastMsg.getText());
                                    msgDto.setCreatedAt(lastMsg.getCreatedAt());
                                    dto.setLastMessage(msgDto);
                                    return dto;
                                })
                                .defaultIfEmpty(dto);
                    }

                    return Mono.just(dto);
                })
                .onErrorResume(error -> {
                    log.error("Error enriqueciendo conversación: {}", error.getMessage());
                    return Mono.just(dto);
                });
    }

    private String buildDisplayName(UserSecurityDto user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = user.getEmail();

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
    private Mono<UserSecurityDto> getUserByEmailFromSecurity(String email) {
        return webClient.get()
                .uri("/users/by-email/{email}", email)
                .retrieve()
                .bodyToMono(UserSecurityDto.class)
                .doOnError(error -> log.error("Error obteniendo usuario por email {}: {}", email, error.getMessage()));
    }

    /**
     * Obtener usuario por ID desde msvc-security usando WebClient
     */
    private Mono<UserSecurityDto> getUserByIdFromSecurity(String userId) {
        return webClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserSecurityDto.class)
                .doOnError(error -> log.error("Error obteniendo usuario por ID {}: {}", userId, error.getMessage()));
    }
}