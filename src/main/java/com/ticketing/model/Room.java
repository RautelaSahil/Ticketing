package com.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    @Indexed(unique = true)
    private String roomNumber;

    private int capacity;

    // e.g. { "floor": 1, "hasProjector": true }
    private Map<String, Object> metadata;

    // Administrative flag only. Occupancy is derived from reservations.
    @Builder.Default
    private boolean active = true;
}