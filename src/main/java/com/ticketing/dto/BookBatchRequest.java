package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request payload for multi-room batch reservations in Phase 3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookBatchRequest {

    private List<String> roomIds;
    private Instant slotStart;
}
