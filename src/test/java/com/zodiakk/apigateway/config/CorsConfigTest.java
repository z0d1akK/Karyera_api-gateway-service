package com.zodiakk.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins",
                new String[]{"http://localhost:5173", "https://karyera.example"});
    }

    @Test
    @DisplayName("configures CORS for every path")
    void configuresCorsForEveryPath() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        CorsConfiguration configuration = source.getCorsConfiguration(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/employees").build())
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://karyera.example");
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }
}
