package com.example.back.auth;

import com.example.back.config.AuthProperties;
import com.example.back.user.Role;
import com.example.back.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Signed JWT (HS256) carrying an identity, its pseudo and role. The subject is the user id for
 * accounts, the guest id for guests.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    public static final String ISSUER = "yohan";
    public static final String CLAIM_PSEUDO = "pseudo";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    /** Short-lived token of an account, renewed through the refresh token. */
    public String issue(User user) {
        return encode(user.getId(), user.getPseudo(), user.getRole(), properties.accessTokenTtl());
    }

    /** Single guest token: no refresh, the guest identity ends with it. */
    public String issueGuest(UUID guestId, String pseudo) {
        return encode(guestId, pseudo, Role.GUEST, properties.guestTokenTtl());
    }

    private String encode(UUID subject, String pseudo, Role role, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .subject(subject.toString())
                        .issuedAt(now)
                        .expiresAt(now.plus(ttl))
                        .claim(CLAIM_PSEUDO, pseudo)
                        .claim(CLAIM_ROLE, role.name())
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
