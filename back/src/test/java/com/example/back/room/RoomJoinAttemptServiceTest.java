package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.back.MutableClock;
import com.example.back.config.RoomProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RoomJoinAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    private final RoomJoinAttemptService service =
            new RoomJoinAttemptService(
                    new RoomProperties("http://localhost", 5, Duration.ofMinutes(1)), clock);

    @Test
    void blocksAfterFiveFailuresUntilWindowEnds() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.1.1.1", "ABCDEFGH");
        }
        assertThat(service.blockedFor("1.1.1.1", "ABCDEFGH")).isEmpty();

        clock.advance(Duration.ofSeconds(20));
        service.recordFailure("1.1.1.1", "ABCDEFGH");

        assertThat(service.blockedFor("1.1.1.1", "ABCDEFGH")).contains(Duration.ofSeconds(40));
        clock.advance(Duration.ofSeconds(40));
        assertThat(service.blockedFor("1.1.1.1", "ABCDEFGH")).isEmpty();
    }

    @Test
    void blockIsPerIpAndRoom() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("1.1.1.1", "ABCDEFGH");
        }
        assertThat(service.blockedFor("2.2.2.2", "ABCDEFGH")).isEmpty();
        assertThat(service.blockedFor("1.1.1.1", "HGFEDCBA")).isEmpty();
    }

    @Test
    void failuresOfAnEndedWindowAreForgotten() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.1.1.1", "ABCDEFGH");
        }
        clock.advance(Duration.ofMinutes(1));
        service.recordFailure("1.1.1.1", "ABCDEFGH");

        assertThat(service.blockedFor("1.1.1.1", "ABCDEFGH")).isEmpty();
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.1.1.1", "ABCDEFGH");
        }
        service.reset("1.1.1.1", "ABCDEFGH");
        service.recordFailure("1.1.1.1", "ABCDEFGH");

        assertThat(service.blockedFor("1.1.1.1", "ABCDEFGH")).isEmpty();
    }
}
