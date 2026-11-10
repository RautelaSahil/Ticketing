package com.ticketing.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Pure utility functions for room reservation 5-minute time boundaries.
 * All computations are in UTC with seconds and nanoseconds zeroed out.
 */
public final class TimeSlotUtils {

    public static final int INTERVAL_MINUTES = 5;
    public static final Duration INTERVAL_DURATION = Duration.ofMinutes(INTERVAL_MINUTES);

    private TimeSlotUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Calculates the start time of the next valid 5-minute interval.
     * Rule: Floor current time to the 5-minute boundary, then add one interval.
     * E.g.
     * 12:00:00.000 -> 12:05:00.000
     * 12:01:30.500 -> 12:05:00.000
     * 12:04:59.999 -> 12:05:00.000
     * 12:05:00.000 -> 12:10:00.000
     */
    public static Instant getNextIntervalStart(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Current time 'now' cannot be null");
        }

        ZonedDateTime utcTime = now.atZone(ZoneOffset.UTC);
        int minute = utcTime.getMinute();
        int flooredMinute = (minute / INTERVAL_MINUTES) * INTERVAL_MINUTES;

        ZonedDateTime flooredTime = utcTime
                .withMinute(flooredMinute)
                .withSecond(0)
                .withNano(0);

        return flooredTime.plus(INTERVAL_DURATION).toInstant();
    }

    /**
     * Calculates the end time of an interval given its start time.
     * E.g., 12:05:00.000 -> 12:10:00.000
     */
    public static Instant getIntervalEnd(Instant slotStart) {
        if (slotStart == null) {
            throw new IllegalArgumentException("Slot start time cannot be null");
        }
        return slotStart.plus(INTERVAL_DURATION);
    }

    /**
     * Validates whether a client-provided slot start exactly matches
     * the server's expected next valid interval.
     */
    public static boolean isValidNextInterval(Instant requestedSlotStart, Instant now) {
        if (requestedSlotStart == null || now == null) {
            return false;
        }
        return requestedSlotStart.equals(getNextIntervalStart(now));
    }

    /**
     * Checks if current time falls within an active slot:
     * slotStart <= now < slotEnd
     */
    public static boolean isWithinActiveSlot(Instant slotStart, Instant slotEnd, Instant now) {
        if (slotStart == null || slotEnd == null || now == null) {
            return false;
        }
        return !now.isBefore(slotStart) && now.isBefore(slotEnd);
    }

    /**
     * Computes remaining seconds until slotEnd, clamped to a minimum of 0.
     */
    public static long getRemainingSeconds(Instant slotEnd, Instant now) {
        if (slotEnd == null || now == null) {
            return 0L;
        }
        long seconds = Duration.between(now, slotEnd).toSeconds();
        return Math.max(0L, seconds);
    }
}
