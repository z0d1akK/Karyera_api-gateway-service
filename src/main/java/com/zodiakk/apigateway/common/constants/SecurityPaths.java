package com.zodiakk.apigateway.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityPaths {

    public static final String INTERNAL_VALIDATE = "/internal/auth/validate";

    public static final String SWAGGER_UI = "/swagger-ui";

    public static final String SWAGGER_HTML = "/swagger-ui.html";

    public static final String API_DOCS = "/v3/api-docs";

    public static final String GATEWAY_API_DOCS = "/api-docs";

    public static final String AUTH_DOCS = "/auth-service/api-docs";

    public static final String EMPLOYEE_DOCS = "/employee-service/api-docs";

    public static final String ACTUATOR_HEALTH = "/actuator/health";

    public static final String ACTUATOR_INFO = "/actuator/info";

    public static final String ERROR = "/error";
}
