package com.ticketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for room bookings in Phase 3 & Phase 4.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ticketing.booking")
public class BookingProperties {

    /**
     * Maximum number of unique rooms allowed per batch reservation request.
     * Defaults to 5.
     */
    private int maxRoomsPerRequest = 5;

    /**
     * Future booking horizon in hours (e.g. 24 hours ahead).
     * Defaults to 24.
     */
    private int futureWindowHours = 24;

    /**
     * Cancellation & queue joining cutoff in minutes (e.g. 10 minutes).
     * Cancellations and queue joins are strictly rejected at <= cutoff.
     * Defaults to 10.
     */
    private int cancellationCutoffMinutes = 10;

    /**
     * Maximum number of upcoming live (BOOKED + SCHEDULED) reservations
     * allowed per user. Defaults to 12.
     */
    private int maxFutureReservationsPerUser = 12;

    public Duration getFutureWindowDuration() {
        return Duration.ofHours(futureWindowHours);
    }

    public Duration getCancellationCutoffDuration() {
        return Duration.ofMinutes(cancellationCutoffMinutes);
    }
}
