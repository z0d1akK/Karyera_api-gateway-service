package com.zodiakk.apigateway.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Headers {

    public static final String AUTHORIZATION = "Authorization";

    public static final String USER_ID = "X-User-Id";

    public static final String USER_ROLE = "X-User-Role";

    public static final String USER_EMAIL = "X-User-Email";

    public static final String INTERNAL_KEY = "X-Internal-Key";
}
