package com.msvcchat.service.Impl;

import com.msvcchat.dtos.*;
import com.msvcchat.dtos.security.SimpleRoleDto;
import com.msvcchat.dtos.security.SimpleUserDto;
import com.msvcchat.entity.ChatMessage;
import com.msvcchat.entity.ConversationDocument;
import com.msvcchat.mappers.ConversationMapper;
import com.msvcchat.repositories.ConversationRepository;
import com.msvcchat.service.ChatRoomManager;
import com.msvcchat.service.ConversationService;
import com.msvcchat.service.ExternalServiceClient;
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

import static com.msvcchat.helpers.ChatHelper.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ConversationMapper conversationMapper;
    private final ChatRoomManager chatRoomManager;
    private final ExternalServiceClient externalServiceClient;
    @Qualifier("securityWebClient")
    private final WebClient securityWebClient;

    @Qualifier("membersWebClient")
    private final WebClient membersWebClient;


    @Override
    public Flux<ConversationDto> getConversationsByUser(String userEmail) {
        return conversationRepository.findByParticipantsContaining(userEmail)
                .flatMap(conv -> enrichConversationDto(conv, userEmail));
    }

    @Override
    public Mono<ConversationDto> createConversation(String userEmail, CreateConversationDto dto) {
        return externalServiceClient.getUserByIdFromSecurity(dto.participantId())
                .flatMap(participantUser -> {
                    String participantEmail = participantUser.getEmail();
                    return conversationRepository.findByParticipantsContainingAll(Set.of(userEmail, participantEmail))
                            .switchIfEmpty(
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

    @Override
    public Flux<UserConnectionDto> getAllUsersByRole(String role) {
        return securityWebClient.get()
                .uri("/users/by-role/{role}", role)
                .retrieve()
                .bodyToFlux(SimpleUserDto.class)
                .flatMap(user -> {
                    log.info("Usuario traído de security {}", user);
                    return externalServiceClient.getMemberFromMembers(user.id())
                            .map(memberDto -> {
                                String displayName = buildDisplayName(memberDto.firstName(), memberDto.lastName());
                                String initials = generateInitials(memberDto.firstName(), memberDto.lastName());
                                boolean isConnected = checkUserConnection(user.id());

                                return new UserConnectionDto(
                                        user.id(),
                                        displayName,
                                        isConnected,
                                        memberDto.profileImageUrl(),
                                        initials
                                );
                            })
                            .onErrorResume(error -> {
                                log.warn("Error obteniendo member para userId {}: {}", user.id(), error.getMessage());
                                String fallbackName = user.email() != null ? user.email() : "Usuario";
                                String initials = generateInitialsFromEmail(user.email());

                                return Mono.just(new UserConnectionDto(
                                        user.id(),
                                        fallbackName,
                                        false,
                                        null,
                                        initials
                                ));
                            });
                })
                .doOnError(error -> log.error("Error obteniendo usuarios por rol {}: {}", role, error.getMessage()));
    }


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

        return externalServiceClient.getUserByEmailFromSecurity(participantEmail)
                .flatMap(userSecurityDto -> {
                    String userId = userSecurityDto.id();

                    return externalServiceClient.getMemberFromMembers(userId)
                            .map(memberDto -> {
                                String displayName = buildDisplayName(
                                        memberDto.firstName(),
                                        memberDto.lastName()
                                );

                                String mainRole = userSecurityDto.roles().stream()
                                        .findFirst()
                                        .map(SimpleRoleDto::name)
                                        .orElse("USER");

                                UserDto enrichedUser = new UserDto(
                                        userId,
                                        displayName,
                                        memberDto.profileImageUrl()
                                );

                                dto.setParticipant(enrichedUser);
                                return dto;
                            })
                            .onErrorResume(error -> {
                                log.warn("⚠️ No se pudo obtener datos de members para userId={}: {}",
                                        userId, error.getMessage());

                                String fallbackName = buildDisplayName(
                                        userSecurityDto.firstName(),
                                        userSecurityDto.lastName()
                                );

                                UserDto partialUser = new UserDto(
                                        userId,
                                        fallbackName,
                                        null
                                );

                                dto.setParticipant(partialUser);
                                return Mono.just(dto);
                            });
                })
                .flatMap(enrichedDto -> {
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


    private boolean checkUserConnection(String userId) {
        return chatRoomManager.getAllRooms().stream().anyMatch(roomId -> chatRoomManager.getUsersInRoom(roomId).contains(userId));
    }

}