package com.example.back.auth;

import com.example.back.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Blocks a (pseudo, IP) pair for {@code loginBlockDuration} after {@code loginMaxFailures} failed
 * logins within that same duration. In-memory: counters are per application instance and reset on
 * restart.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AuthProperties properties;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {}

    /** Remaining block duration, or empty if the pair may try to log in. */
    public Optional<Duration> blockedFor(String pseudo, String ip) {
        Attempts current = attempts.get(key(pseudo, ip));
        Instant now = clock.instant();
        if (current == null
                || current.blockedUntil() == null
                || !current.blockedUntil().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.blockedUntil()));
    }

    public void recordFailure(String pseudo, String ip) {
        Instant now = clock.instant();
        Duration window = properties.loginBlockDuration();
        attempts.compute(
                key(pseudo, ip),
                (k, current) -> {
                    if (current == null || isStale(current, now)) {
                        current = new Attempts(0, now, null);
                    }
                    int failures = current.failures() + 1;
                    Instant blockedUntil =
                            failures >= properties.loginMaxFailures() ? now.plus(window) : null;
                    return new Attempts(failures, current.windowStart(), blockedUntil);
                });
    }

    public void reset(String pseudo, String ip) {
        attempts.remove(key(pseudo, ip));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void evictStale() {
        Instant now = clock.instant();
        attempts.values().removeIf(a -> isStale(a, now));
    }

    private boolean isStale(Attempts a, Instant now) {
        boolean windowOver = !a.windowStart().plus(properties.loginBlockDuration()).isAfter(now);
        boolean notBlocked = a.blockedUntil() == null || !a.blockedUntil().isAfter(now);
        return windowOver && notBlocked;
    }

    private static String key(String pseudo, String ip) {
        return pseudo + '|' + ip;
    }
}
