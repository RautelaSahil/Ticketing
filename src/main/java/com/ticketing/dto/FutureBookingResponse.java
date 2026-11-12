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
public class FutureBookingResponse {
    private String reservationId;
    private String roomId;
    private String roomNumber;
    private Instant slotStart;
    private Instant slotEnd;
    private ReservationStatus status;
    private String message;
}
