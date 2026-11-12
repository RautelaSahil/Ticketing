package com.ticketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for room bookings in Phase 3.
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
}
