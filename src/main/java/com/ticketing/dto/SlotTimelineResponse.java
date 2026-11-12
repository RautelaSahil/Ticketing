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
public class SlotTimelineResponse {
    private String roomId;
    private String roomNumber;
    private Instant serverTime;
    private Instant windowStart;
    private Instant windowEnd;
    private List<SlotTimelineItemDto> slots;
}
