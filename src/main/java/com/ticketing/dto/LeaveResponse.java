package com.ticketing.dto;

import com.ticketing.model.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {

    private String message;
    private String reservationId;
    private ReservationStatus status;
}
