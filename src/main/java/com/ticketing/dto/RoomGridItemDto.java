package com.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomGridItemDto {

    private String roomId;
    private String roomNumber;
    private int capacity;
    private Map<String, Object> metadata;
    private boolean occupied;
    private boolean yours;
}
