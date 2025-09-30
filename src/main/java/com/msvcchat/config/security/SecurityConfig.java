package com.msvcchat.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http.authorizeExchange(exchanges -> exchanges.pathMatchers(
                                        "/actuator/**",
                                        "swagger-ui/**",
                                        "v3/api-docs/**",
                                        "/ws/chat",
                                        "/test/**"
                                ).permitAll()
                                .anyExchange().authenticated()
                ).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
