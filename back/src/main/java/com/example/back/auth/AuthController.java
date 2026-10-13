package com.example.back.auth;

import com.example.back.user.Role;
import com.example.back.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;

    private final AuthService authService;
    private final AuthCookies authCookies;
    private final GuestService guestService;

    public record RegisterRequest(
            @NotNull @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH) String password) {}

    public record LoginRequest(
            @NotBlank @Size(max = 64) String pseudo,
            @NotBlank @Size(max = PASSWORD_MAX_LENGTH) String password) {}

    public record UserResponse(String pseudo, String role) {
        static UserResponse of(User user) {
            return new UserResponse(user.getPseudo(), user.getRole().name());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.Session session = authService.register(request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(authCookies.forSession(session))
                .body(UserResponse.of(session.user()));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.Session session =
                authService.login(request.pseudo(), request.password(), http.getRemoteAddr());
        return ResponseEntity.ok()
                .headers(authCookies.forSession(session))
                .body(UserResponse.of(session.user()));
    }

    /**
     * On failure the cookies are left untouched: with several tabs, a request that lost the
     * rotation race must not erase the fresh cookies another tab has just received.
     */
    @PostMapping("/refresh")
    public ResponseEntity<UserResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        return authService
                .refresh(refreshToken)
                .map(
                        session ->
                                ResponseEntity.ok()
                                        .headers(authCookies.forSession(session))
                                        .body(UserResponse.of(session.user())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * "Jouer en invité". A visitor coming back with a still valid guest cookie keeps the same guest
     * id and pseudo; the cookie is then left as is. Becoming a guest closes any account session
     * still held in the refresh cookie.
     */
    @PostMapping("/guest")
    public ResponseEntity<UserResponse> guest(
            @CookieValue(name = AuthCookies.ACCESS, required = false) String accessToken,
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        GuestService.GuestSession guest = guestService.enter(accessToken);
        UserResponse body = new UserResponse(guest.pseudo(), Role.GUEST.name());
        if (!guest.isNew()) {
            return ResponseEntity.ok(body);
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok().headers(authCookies.forGuest(guest.token())).body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().headers(authCookies.cleared()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(
                jwt.getClaimAsString(AccessTokenService.CLAIM_PSEUDO),
                jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE));
    }
}
