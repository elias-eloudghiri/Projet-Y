package com.example.back.auth;

import com.example.back.config.AuthProperties;
import com.example.back.user.User;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Short-lived signed JWT (HS256) carrying the user id, pseudo and role. */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    public static final String ISSUER = "yohan";
    public static final String CLAIM_PSEUDO = "pseudo";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(CLAIM_PSEUDO, user.getPseudo())
                .claim(CLAIM_ROLE, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
