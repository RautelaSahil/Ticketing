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

    /**
     * Checks if an instant lies strictly on a 5-minute boundary in UTC
     * with seconds and nanoseconds zeroed out (e.g. 12:00:00.000, 12:05:00.000).
     */
    public static boolean isOnBoundary(Instant instant) {
        if (instant == null) {
            return false;
        }
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        return utc.getSecond() == 0 && utc.getNano() == 0 && (utc.getMinute() % INTERVAL_MINUTES == 0);
    }

    /**
     * Validates if a slot start is on a 5-minute boundary and falls within
     * the allowed future booking window [getNextIntervalStart(now), now + windowDuration].
     */
    public static boolean isWithinFutureWindow(Instant slotStart, Instant now, Duration windowDuration) {
        if (slotStart == null || now == null || windowDuration == null) {
            return false;
        }
        if (!isOnBoundary(slotStart)) {
            return false;
        }
        Instant nextIntervalStart = getNextIntervalStart(now);
        Instant maxWindowEnd = now.plus(windowDuration);
        return !slotStart.isBefore(nextIntervalStart) && !slotStart.isAfter(maxWindowEnd);
    }

    /**
     * Enforces the strict cutoff rule (e.g. 10 minutes):
     * Action is allowed if and only if slotStart - now > cutoff.
     * E.g. with 10 min cutoff: 10m01s is true; 10m00s and 9m59s are false.
     */
    public static boolean isCancellationAllowed(Instant slotStart, Instant now, Duration cutoff) {
        if (slotStart == null || now == null || cutoff == null) {
            return false;
        }
        return now.plus(cutoff).isBefore(slotStart);
    }

    /**
     * Queue joining is allowed only when more than cutoff (e.g. 10 minutes) remains before start.
     */
    public static boolean canJoinQueue(Instant slotStart, Instant now, Duration cutoff) {
        return isCancellationAllowed(slotStart, now, cutoff);
    }

    /**
     * Generates an unmodifiable list of all 5-minute interval start times
     * from getNextIntervalStart(now) up to now + windowDuration.
     */
    public static java.util.List<Instant> generateFutureSlotStarts(Instant now, Duration windowDuration) {
        if (now == null || windowDuration == null) {
            return java.util.List.of();
        }
        Instant start = getNextIntervalStart(now);
        Instant end = now.plus(windowDuration);
        java.util.List<Instant> slots = new java.util.ArrayList<>();
        Instant current = start;
        while (!current.isAfter(end)) {
            slots.add(current);
            current = current.plus(INTERVAL_DURATION);
        }
        return java.util.Collections.unmodifiableList(slots);
    }
}
