package com.msvcchat.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final ReactiveJwtDecoder jwtDecoder;

    public Mono<Jwt> validateToken(String token) {
        return jwtDecoder.decode(token);
    }
}
