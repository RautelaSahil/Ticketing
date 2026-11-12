package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotTimelineItemDto {
    private Instant slotStart;
    private Instant slotEnd;
    private SlotAction action;
    private long waitingCount;
    private boolean cancellable;
    private Instant cancellationDeadline;
}
