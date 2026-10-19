package com.example.back.config;

import com.example.back.auth.AccessTokenService;
import com.example.back.auth.AuthCookies;
import com.example.back.user.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.util.WebUtils;

@Configuration
public class SecurityConfig {

    /** Auth endpoints that must work even with an expired or invalid access cookie. */
    private static final Set<String> PUBLIC_AUTH_PATHS =
            Set.of(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/auth/guest");

    /** Roles that come with an account; guests are denied the routes reserved to them. */
    private static final String[] ACCOUNT_ROLES = {Role.USER.name(), Role.ADMIN.name()};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF: cookies are SameSite=Strict and the API only accepts JSON, so Spring's CSRF token
        // is not used.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_AUTH_PATHS.toArray(String[]::new))
                                        .permitAll()
                                        .requestMatchers(
                                                "/actuator/health/**", "/actuator/health", "/error")
                                        .permitAll()
                                        // Story catalogue (US-03) and any other admin route.
                                        .requestMatchers("/api/admin/**")
                                        .hasRole(Role.ADMIN.name())
                                        // Room creation (US-04) requires an account.
                                        .requestMatchers(HttpMethod.POST, "/api/rooms")
                                        .hasAnyRole(ACCOUNT_ROLES)
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.bearerTokenResolver(cookieTokenResolver())
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // argon2id with OWASP minimum parameters: 19 MiB memory, 2 iterations, parallelism 1.
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(secretKey(properties))
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
        // Expiry checked against the application clock, the one used to issue the tokens.
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestamps, new JwtIssuerValidator(AccessTokenService.ISSUER)));
        return decoder;
    }

    private static SecretKey secretKey(AuthProperties properties) {
        return new SecretKeySpec(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static BearerTokenResolver cookieTokenResolver() {
        return request -> {
            if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
                return null;
            }
            Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS);
            return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessTokenService.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
