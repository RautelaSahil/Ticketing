package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyFutureReservationsResponse {
    private Instant serverTime;
    private int totalCount;
    private List<FutureReservationSummaryDto> reservations;
}
