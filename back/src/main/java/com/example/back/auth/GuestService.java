package com.example.back.auth;

import com.example.back.user.PseudoGenerator;
import com.example.back.user.Role;
import com.example.back.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Guest access: the identity (guest id + pseudo) only lives in a signed JWT, nothing is written to
 * the database.
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    private static final int MAX_PSEUDO_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final PseudoGenerator pseudoGenerator;
    private final AccessTokenService accessTokenService;
    private final JwtDecoder jwtDecoder;

    /**
     * @param token the newly issued JWT, or {@code null} when the visitor kept a still valid guest
     *     token (its lifetime is not extended: there is no refresh for guests)
     */
    public record GuestSession(UUID guestId, String pseudo, String token) {
        public boolean isNew() {
            return token != null;
        }
    }

    /** Returns the current guest identity if the token is still valid, otherwise a new one. */
    public GuestSession enter(String currentToken) {
        return current(currentToken).orElseGet(this::create);
    }

    private Optional<GuestSession> current(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (!Role.GUEST.name().equals(jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE))) {
                return Optional.empty();
            }
            return Optional.of(
                    new GuestSession(
                            UUID.fromString(jwt.getSubject()),
                            jwt.getClaimAsString(AccessTokenService.CLAIM_PSEUDO),
                            null));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private GuestSession create() {
        // Account pseudos are unique in the database; a guest must not impersonate one of them.
        for (int attempt = 0; attempt < MAX_PSEUDO_ATTEMPTS; attempt++) {
            String pseudo = pseudoGenerator.generate();
            if (!userRepository.existsByPseudo(pseudo)) {
                UUID guestId = UUID.randomUUID();
                return new GuestSession(
                        guestId, pseudo, accessTokenService.issueGuest(guestId, pseudo));
            }
        }
        throw new IllegalStateException("Could not generate a guest pseudo");
    }
}
