package com.example.back.room;

import com.example.back.config.RoomProperties;
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
 * Refuses join attempts of an (IP, room code) pair once {@code joinMaxFailures} wrong passwords
 * were given within a {@code joinFailureWindow} starting at the first one. In-memory: counters are
 * per application instance and reset on restart.
 */
@Service
@RequiredArgsConstructor
public class RoomJoinAttemptService {

    private final RoomProperties properties;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant windowStart) {}

    /** Time until the window ends, or empty if the pair may try a password. */
    public Optional<Duration> blockedFor(String ip, String code) {
        Attempts current = attempts.get(key(ip, code));
        Instant now = clock.instant();
        if (current == null
                || isStale(current, now)
                || current.failures() < properties.joinMaxFailures()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, windowEnd(current)));
    }

    public void recordFailure(String ip, String code) {
        Instant now = clock.instant();
        attempts.compute(
                key(ip, code),
                (k, current) ->
                        current == null || isStale(current, now)
                                ? new Attempts(1, now)
                                : new Attempts(current.failures() + 1, current.windowStart()));
    }

    public void reset(String ip, String code) {
        attempts.remove(key(ip, code));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void evictStale() {
        Instant now = clock.instant();
        attempts.values().removeIf(a -> isStale(a, now));
    }

    private boolean isStale(Attempts a, Instant now) {
        return !windowEnd(a).isAfter(now);
    }

    private Instant windowEnd(Attempts a) {
        return a.windowStart().plus(properties.joinFailureWindow());
    }

    private static String key(String ip, String code) {
        return ip + '|' + code;
    }
}
