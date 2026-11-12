package com.ticketing.dto;

import com.ticketing.model.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelReservationResponse {
    private String reservationId;
    private ReservationStatus previousStatus;
    private Instant cancelledAt;
    private String message;
}
