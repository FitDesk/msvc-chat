package com.msvcchat.service;

import com.msvcchat.dtos.members.MemberDto;
import com.msvcchat.dtos.security.SimpleUserDto;
import com.msvcchat.dtos.security.UserSecurityDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExternalServiceClient {
    Mono<SimpleUserDto> getUserByEmailFromSecurity(String email);
    Mono<MemberDto> getMemberFromMembers(String userId);
    Mono<UserSecurityDto> getUserByIdFromSecurity(String userId);
    Flux<SimpleUserDto> getUsersByRoleFromSecurity(String role);
}
