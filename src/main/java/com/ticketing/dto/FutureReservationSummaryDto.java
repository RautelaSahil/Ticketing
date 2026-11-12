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
public class FutureReservationSummaryDto {
    private String reservationId;
    private String roomId;
    private String roomNumber;
    private Instant slotStart;
    private Instant slotEnd;
    private ReservationStatus status;
    private boolean cancellable;
    private Instant cancellationDeadline;
    private Instant createdAt;
}
