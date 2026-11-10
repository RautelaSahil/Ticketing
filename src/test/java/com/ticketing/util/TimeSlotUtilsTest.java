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
}
