package com.msvcchat.service.Impl;

import com.msvcchat.dtos.members.MemberDto;
import com.msvcchat.dtos.security.SimpleUserDto;
import com.msvcchat.dtos.security.UserSecurityDto;
import com.msvcchat.service.ExternalServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalServiceClientImpl implements ExternalServiceClient {

    @Qualifier("securityWebClient")
    private final WebClient securityWebClient;

    @Qualifier("membersWebClient")
    private final WebClient membersWebClient;

    @Override
    public Mono<SimpleUserDto> getUserByEmailFromSecurity(String email) {
        return securityWebClient.get()
                .uri("/users/by-email/{email}", email)
                .retrieve()
                .bodyToMono(SimpleUserDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo usuario de security por email {}: {}",
                        email, error.getMessage()));
    }

    @Override
    public Mono<MemberDto> getMemberFromMembers(String userId) {
        return membersWebClient.get()
                .uri("/public/member/{userId}", userId)
                .retrieve()
                .bodyToMono(MemberDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo miembro de members para userId {}: {}",
                        userId, error.getMessage()));
    }

    @Override
    public Mono<UserSecurityDto> getUserByIdFromSecurity(String userId) {
        return securityWebClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserSecurityDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo usuario de security por ID {}: {}",
                        userId, error.getMessage()));
    }

    @Override
    public Flux<SimpleUserDto> getUsersByRoleFromSecurity(String role) {
        return securityWebClient.get()
                .uri("/users/by-role/{role}", role)
                .retrieve()
                .bodyToFlux(SimpleUserDto.class)
                .doOnError(error -> log.error("❌ Error obteniendo usuarios por rol {}: {}",
                        role, error.getMessage()));
    }
}
