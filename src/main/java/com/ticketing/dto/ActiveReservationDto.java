package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveReservationDto {

    private String reservationId;
    private String roomId;
    private String roomNumber;
    private int capacity;
    private Map<String, Object> metadata;
    private Instant slotStart;
    private Instant slotEnd;
    private long remainingSeconds;
}
