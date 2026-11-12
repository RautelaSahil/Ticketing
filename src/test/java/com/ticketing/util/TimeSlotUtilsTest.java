package com.ticketing.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotUtilsTest {

    @Test
    @DisplayName("getNextIntervalStart should snap accurately to next 5-minute boundary")
    void shouldCalculateNextIntervalStart() {
        // At 12:00:00.000 -> next is 12:05:00.000
        Instant at1200 = Instant.parse("2026-11-10T12:00:00.000Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at1200))
                .isEqualTo(Instant.parse("2026-11-10T12:05:00.000Z"));

        // At 12:01:30.500 -> next is 12:05:00.000
        Instant at1201 = Instant.parse("2026-11-10T12:01:30.500Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at1201))
                .isEqualTo(Instant.parse("2026-11-10T12:05:00.000Z"));

        // At 12:04:59.999 -> next is 12:05:00.000
        Instant at1204 = Instant.parse("2026-11-10T12:04:59.999Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at1204))
                .isEqualTo(Instant.parse("2026-11-10T12:05:00.000Z"));

        // At 12:05:00.000 exact -> next is 12:10:00.000
        Instant at1205 = Instant.parse("2026-11-10T12:05:00.000Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at1205))
                .isEqualTo(Instant.parse("2026-11-10T12:10:00.000Z"));

        // End of hour transition: 12:56:00 -> 13:00:00
        Instant at1256 = Instant.parse("2026-11-10T12:56:00.000Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at1256))
                .isEqualTo(Instant.parse("2026-11-10T13:00:00.000Z"));

        // Midnight transition: 23:58:30 -> next day 00:00:00
        Instant at2358 = Instant.parse("2026-11-10T23:58:30.000Z");
        assertThat(TimeSlotUtils.getNextIntervalStart(at2358))
                .isEqualTo(Instant.parse("2026-11-11T00:00:00.000Z"));
    }

    @Test
    @DisplayName("getIntervalEnd should return slotStart + 5 minutes with zero nanos")
    void shouldCalculateIntervalEnd() {
        Instant slotStart = Instant.parse("2026-11-10T12:05:00.000Z");
        assertThat(TimeSlotUtils.getIntervalEnd(slotStart))
                .isEqualTo(Instant.parse("2026-11-10T12:10:00.000Z"));
    }

    @Test
    @DisplayName("isValidNextInterval should strictly accept only the exact next interval start")
    void shouldValidateRequestedInterval() {
        Instant now = Instant.parse("2026-11-10T12:01:00.000Z");
        Instant validNext = Instant.parse("2026-11-10T12:05:00.000Z");
        Instant staleCurrent = Instant.parse("2026-11-10T12:00:00.000Z");
        Instant farFuture = Instant.parse("2026-11-10T12:15:00.000Z");

        assertThat(TimeSlotUtils.isValidNextInterval(validNext, now)).isTrue();
        assertThat(TimeSlotUtils.isValidNextInterval(staleCurrent, now)).isFalse();
        assertThat(TimeSlotUtils.isValidNextInterval(farFuture, now)).isFalse();
        assertThat(TimeSlotUtils.isValidNextInterval(null, now)).isFalse();
        assertThat(TimeSlotUtils.isValidNextInterval(validNext, null)).isFalse();
    }

    @Test
    @DisplayName("isWithinActiveSlot should correctly evaluate slotStart <= now < slotEnd")
    void shouldCheckWithinActiveSlot() {
        Instant start = Instant.parse("2026-11-10T12:00:00.000Z");
        Instant end = Instant.parse("2026-11-10T12:05:00.000Z");

        // Before slot
        assertThat(TimeSlotUtils.isWithinActiveSlot(start, end, Instant.parse("2026-11-10T11:59:59.999Z"))).isFalse();

        // Exactly at start
        assertThat(TimeSlotUtils.isWithinActiveSlot(start, end, start)).isTrue();

        // Mid-slot
        assertThat(TimeSlotUtils.isWithinActiveSlot(start, end, Instant.parse("2026-11-10T12:02:30.000Z"))).isTrue();

        // Exactly at end (slot has elapsed)
        assertThat(TimeSlotUtils.isWithinActiveSlot(start, end, end)).isFalse();

        // After slot
        assertThat(TimeSlotUtils.isWithinActiveSlot(start, end, Instant.parse("2026-11-10T12:05:00.001Z"))).isFalse();
    }

    @Test
    @DisplayName("getRemainingSeconds should compute countdown and clamp to zero")
    void shouldCalculateRemainingSeconds() {
        Instant end = Instant.parse("2026-11-10T12:05:00.000Z");

        // 2 minutes (120 seconds) before end
        Instant twoMinutesBefore = Instant.parse("2026-11-10T12:03:00.000Z");
        assertThat(TimeSlotUtils.getRemainingSeconds(end, twoMinutesBefore)).isEqualTo(120L);

        // Past end -> clamps to 0
        Instant pastEnd = Instant.parse("2026-11-10T12:05:10.000Z");
        assertThat(TimeSlotUtils.getRemainingSeconds(end, pastEnd)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Null inputs should be handled safely")
    void shouldHandleNullInputs() {
        assertThatThrownBy(() -> TimeSlotUtils.getNextIntervalStart(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TimeSlotUtils.getIntervalEnd(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isOnBoundary should strictly accept only 5-minute boundaries with zero seconds and nanos")
    void shouldValidateIsOnBoundary() {
        // Valid 5-minute boundaries
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:00:00.000Z"))).isTrue();
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:05:00.000Z"))).isTrue();
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:55:00.000Z"))).isTrue();
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-11T00:00:00.000Z"))).isTrue();

        // Invalid: minutes not divisible by 5
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:01:00.000Z"))).isFalse();
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:07:00.000Z"))).isFalse();

        // Invalid: seconds non-zero
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:05:01.000Z"))).isFalse();

        // Invalid: nanos non-zero
        assertThat(TimeSlotUtils.isOnBoundary(Instant.parse("2026-11-10T12:05:00.100Z"))).isFalse();

        // Null
        assertThat(TimeSlotUtils.isOnBoundary(null)).isFalse();
    }

    @Test
    @DisplayName("isWithinFutureWindow should validate range [T_next, now + 24h] and reject misaligned/out-of-range slots")
    void shouldValidateFutureWindow() {
        Instant now = Instant.parse("2026-11-10T12:01:30.000Z");
        java.time.Duration window24h = java.time.Duration.ofHours(24);

        Instant nextInterval = Instant.parse("2026-11-10T12:05:00.000Z");
        Instant twoHoursLater = Instant.parse("2026-11-10T14:05:00.000Z");
        Instant exact24hBoundary = Instant.parse("2026-11-11T12:00:00.000Z"); // inside now + 24h (which is 12:01:30)
        Instant outsideWindow = Instant.parse("2026-11-11T12:05:00.000Z");   // 24h 3m 30s ahead -> exceeds now + 24h
        Instant staleCurrent = Instant.parse("2026-11-10T12:00:00.000Z");     // in past
        Instant misalignedInsideWindow = Instant.parse("2026-11-10T14:07:00.000Z");

        assertThat(TimeSlotUtils.isWithinFutureWindow(nextInterval, now, window24h)).isTrue();
        assertThat(TimeSlotUtils.isWithinFutureWindow(twoHoursLater, now, window24h)).isTrue();
        assertThat(TimeSlotUtils.isWithinFutureWindow(exact24hBoundary, now, window24h)).isTrue();
        assertThat(TimeSlotUtils.isWithinFutureWindow(outsideWindow, now, window24h)).isFalse();
        assertThat(TimeSlotUtils.isWithinFutureWindow(staleCurrent, now, window24h)).isFalse();
        assertThat(TimeSlotUtils.isWithinFutureWindow(misalignedInsideWindow, now, window24h)).isFalse();
        assertThat(TimeSlotUtils.isWithinFutureWindow(null, now, window24h)).isFalse();
    }

    @Test
    @DisplayName("isCancellationAllowed should enforce strict > 10m rule (10m01s allows, 10m00s & 9m59s reject)")
    void shouldEnforceCancellationCutoffRule() {
        Instant now = Instant.parse("2026-11-10T12:00:00.000Z");
        java.time.Duration cutoff10m = java.time.Duration.ofMinutes(10);

        // 10m01s ahead -> Allowed
        Instant at10m01s = Instant.parse("2026-11-10T12:10:01.000Z");
        assertThat(TimeSlotUtils.isCancellationAllowed(at10m01s, now, cutoff10m)).isTrue();
        assertThat(TimeSlotUtils.canJoinQueue(at10m01s, now, cutoff10m)).isTrue();

        // 10m00s exact ahead -> Strictly REJECTED
        Instant at10m00s = Instant.parse("2026-11-10T12:10:00.000Z");
        assertThat(TimeSlotUtils.isCancellationAllowed(at10m00s, now, cutoff10m)).isFalse();
        assertThat(TimeSlotUtils.canJoinQueue(at10m00s, now, cutoff10m)).isFalse();

        // 9m59s ahead -> Strictly REJECTED
        Instant at9m59s = Instant.parse("2026-11-10T12:09:59.000Z");
        assertThat(TimeSlotUtils.isCancellationAllowed(at9m59s, now, cutoff10m)).isFalse();
        assertThat(TimeSlotUtils.canJoinQueue(at9m59s, now, cutoff10m)).isFalse();

        // 5m ahead (next interval) -> REJECTED
        Instant at5m00s = Instant.parse("2026-11-10T12:05:00.000Z");
        assertThat(TimeSlotUtils.isCancellationAllowed(at5m00s, now, cutoff10m)).isFalse();
        assertThat(TimeSlotUtils.canJoinQueue(at5m00s, now, cutoff10m)).isFalse();

        // Null checks
        assertThat(TimeSlotUtils.isCancellationAllowed(null, now, cutoff10m)).isFalse();
        assertThat(TimeSlotUtils.isCancellationAllowed(at10m01s, null, cutoff10m)).isFalse();
    }

    @Test
    @DisplayName("generateFutureSlotStarts should generate contiguous sequence of 5m slots")
    void shouldGenerateFutureSlotStartsContiguously() {
        Instant now = Instant.parse("2026-11-10T12:00:00.000Z");
        // 1 hour window from 12:05:00 up to 13:00:00 = 12 slots
        java.time.Duration oneHour = java.time.Duration.ofHours(1);

        java.util.List<Instant> slots = TimeSlotUtils.generateFutureSlotStarts(now, oneHour);

        assertThat(slots).hasSize(12);
        assertThat(slots.get(0)).isEqualTo(Instant.parse("2026-11-10T12:05:00.000Z"));
        assertThat(slots.get(11)).isEqualTo(Instant.parse("2026-11-10T13:00:00.000Z"));

        // Verify each adjacent slot is exactly 5 minutes apart
        for (int i = 0; i < slots.size() - 1; i++) {
            assertThat(java.time.Duration.between(slots.get(i), slots.get(i + 1)))
                    .isEqualTo(java.time.Duration.ofMinutes(5));
        }
    }
}
