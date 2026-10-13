package com.example.back.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.back.MutableClock;
import com.example.back.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    private final AuthProperties properties = new AuthProperties("x".repeat(32), Duration.ofMinutes(15),
            Duration.ofDays(7), Duration.ofSeconds(10), true, 5, Duration.ofMinutes(15), "admin-000000", null);
    private final LoginAttemptService service = new LoginAttemptService(properties, clock);

    @Test
    void blocksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("lerat-000001", "1.1.1.1");
        }
        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).isEmpty();

        service.recordFailure("lerat-000001", "1.1.1.1");

        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).contains(Duration.ofMinutes(15));
    }

    @Test
    void blockIsPerPseudoAndIp() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("lerat-000001", "1.1.1.1");
        }
        assertThat(service.blockedFor("lerat-000001", "2.2.2.2")).isEmpty();
        assertThat(service.blockedFor("loutre-000001", "1.1.1.1")).isEmpty();
    }

    @Test
    void blockExpiresAfterFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("lerat-000001", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(15));

        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).isEmpty();
        service.recordFailure("lerat-000001", "1.1.1.1");
        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).isEmpty();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("lerat-000001", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(16));
        service.recordFailure("lerat-000001", "1.1.1.1");

        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).isEmpty();
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("lerat-000001", "1.1.1.1");
        }
        service.reset("lerat-000001", "1.1.1.1");
        service.recordFailure("lerat-000001", "1.1.1.1");

        assertThat(service.blockedFor("lerat-000001", "1.1.1.1")).isEmpty();
    }
}
