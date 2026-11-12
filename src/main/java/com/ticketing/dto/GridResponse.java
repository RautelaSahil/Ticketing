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
public class GridResponse {

    private Instant serverTime;
    private Instant slotStart;
    private Instant slotEnd;
    private int maxRoomsPerRequest;
    private List<RoomGridItemDto> rooms;
}
