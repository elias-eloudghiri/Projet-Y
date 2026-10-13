package com.example.back.auth;

import com.example.back.config.AuthProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** httpOnly cookies carrying the tokens. The refresh cookie is only sent to /api/auth endpoints. */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    static final String REFRESH_PATH = "/api/auth";

    private final AuthProperties properties;

    public HttpHeaders forSession(AuthService.Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(
                HttpHeaders.SET_COOKIE,
                cookie(ACCESS, session.accessToken(), "/", properties.accessTokenTtl()));
        headers.add(
                HttpHeaders.SET_COOKIE,
                cookie(
                        REFRESH,
                        session.refreshToken(),
                        REFRESH_PATH,
                        properties.refreshTokenTtl()));
        return headers;
    }

    public HttpHeaders cleared() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", "/", Duration.ZERO));
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO));
        return headers;
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
