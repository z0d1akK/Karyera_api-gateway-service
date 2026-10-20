package com.zodiakk.apigateway.config;

import com.zodiakk.apigateway.common.constants.Headers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Value("${services.auth.url}")
    private String authUrl;

    @Bean
    public WebClient authWebClient() {
        return WebClient.builder()
                .baseUrl(authUrl)
                .defaultHeader(Headers.INTERNAL_KEY, internalApiKey)
                .build();
    }
}
