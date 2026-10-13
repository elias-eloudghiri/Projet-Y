package com.example.back;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Test clock that can be moved forward. Truncated to microseconds like PostgreSQL timestamps,
 * otherwise a stored instant may be rounded up and compare after the in-memory one.
 */
public class MutableClock extends Clock {

    private volatile Instant now = currentInstant();

    public void reset() {
        now = currentInstant();
    }

    private static Instant currentInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
