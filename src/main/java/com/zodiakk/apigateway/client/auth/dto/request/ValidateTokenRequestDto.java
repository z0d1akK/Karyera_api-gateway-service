package com.zodiakk.apigateway.client.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateTokenRequestDto {

    @Schema(description = "JWT token")
    @NotBlank
    private String token;
}
