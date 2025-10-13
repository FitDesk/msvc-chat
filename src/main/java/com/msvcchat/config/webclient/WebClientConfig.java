package com.msvcchat.config.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j
public class WebClientConfig {
    @Value("${services.security.url:http://msvc-security:9091}")
    private String securityServiceUrl;

    @Value("${services.members.url:http://msvc-members:9098}")

    private String memberServiceUrl;

    @Bean("securityWebClient")
    public WebClient securityWebClient(WebClient.Builder builder) {
        return builder.baseUrl(securityServiceUrl).build();
    }

    @Bean("membersWebClient")
    public WebClient membersWebClient(WebClient.Builder builder) {
        return builder.baseUrl(memberServiceUrl).build();
    }


}
