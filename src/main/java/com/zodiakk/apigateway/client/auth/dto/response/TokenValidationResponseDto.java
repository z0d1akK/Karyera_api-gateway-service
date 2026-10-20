package com.zodiakk.apigateway.client.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenValidationResponseDto {

    @Schema(description = "Token validation result", example = "true")
    private Boolean valid;

    @Schema(description = "User identifier")
    private UUID userId;

    @Schema(description = "User role", example = "EMPLOYEE")
    private String role;

    @Schema(description = "User email")
    private String email;
}
