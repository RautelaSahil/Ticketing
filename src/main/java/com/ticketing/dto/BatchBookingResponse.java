package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Successful response payload for multi-room batch reservations in Phase 3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchBookingResponse {

    private Instant slotStart;
    private Instant slotEnd;
    private List<ReservationResponse> reservations;
    private String message;
}
