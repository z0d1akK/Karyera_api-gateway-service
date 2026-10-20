package com.zodiakk.apigateway.common.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String AUTHORIZATION_HEADER_MISSING = "Заголовок Authorization отсутствует";

    public static final String AUTHORIZATION_HEADER_INVALID = "Некорректный заголовок Authorization";

    public static final String INVALID_TOKEN = "Недействительный токен";

    public static final String UNAUTHORIZED = "Пользователь не авторизован";

    public static final String AUTH_SERVICE_UNAVAILABLE = "Сервис аутентификации временно недоступен";

    public static final String SERVICE_UNAVAILABLE = "Сервис временно недоступен";

    public static final String ROUTE_NOT_FOUND = "Запрашиваемый ресурс не найден";

    public static final String INTERNAL_SERVER_ERROR = "Внутренняя ошибка сервера";
}
