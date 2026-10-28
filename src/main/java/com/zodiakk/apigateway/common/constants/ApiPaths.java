package com.zodiakk.apigateway.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String LOGIN = "/api/v1/auth/login";

    public static final String REFRESH = "/api/v1/auth/refresh";

    public static final String CHANGE_INITIAL_PASSWORD = "/api/v1/auth/change-initial-password";
}
