package com.zodiakk.apigateway.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(example = "false")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean valid;

    @Schema(example = "401")
    private int status;

    @Schema(example = "UNAUTHORIZED")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    @Schema(example = "Unauthorized")
    private String error;

    @Schema(example = "Пользователь не авторизован")
    private String message;

    @Schema(example = "2026-05-16T15:30:00")
    private LocalDateTime timestamp;
}
