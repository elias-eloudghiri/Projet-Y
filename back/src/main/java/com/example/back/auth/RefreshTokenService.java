package com.example.back.auth;

import com.example.back.config.AuthProperties;
import com.example.back.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 * Presenting an already rotated token revokes its whole family (likely theft), except within a short
 * grace window that absorbs concurrent refreshes from several tabs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public record Rotation(User user, String refreshToken) {
    }

    @Transactional
    public String issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public Optional<Rotation> rotate(String rawToken) {
        Optional<RefreshToken> found = findByRawToken(rawToken);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        Instant now = clock.instant();
        if (!token.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (token.getRevokedAt() != null) {
            handleReuse(token, now);
            return Optional.empty();
        }
        if (repository.revokeIfActive(token.getId(), now) == 0) {
            // Revoked concurrently by another request: inside the grace window by definition.
            return Optional.empty();
        }
        return Optional.of(new Rotation(token.getUser(), issue(token.getUser(), token.getFamilyId())));
    }

    /** Logout: revokes the whole session (family) the token belongs to. */
    @Transactional
    public void revoke(String rawToken) {
        findByRawToken(rawToken).ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(clock.instant());
        log.info("Purged {} expired refresh tokens", deleted);
    }

    private String issue(User user, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.save(new RefreshToken(user, hash(rawToken), familyId, now, now.plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    private void handleReuse(RefreshToken token, Instant now) {
        boolean withinGrace = token.getRevokedAt().plus(properties.refreshReuseGrace()).isAfter(now);
        if (!withinGrace) {
            log.warn("Refresh token reuse detected for user {}: revoking session family {}",
                    token.getUser().getPseudo(), token.getFamilyId());
            repository.revokeFamily(token.getFamilyId(), now);
        }
    }

    private Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(rawToken));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
