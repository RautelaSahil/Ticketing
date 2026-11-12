package com.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
@CompoundIndexes({
        // Only one confirmed owner per room and slot
        @CompoundIndex(
                name = "uniq_booked_room_slot",
                def = "{ 'roomId': 1, 'slotStart': 1 }",
                unique = true,
                partialFilter = "{ 'status': 'BOOKED' }"),

        // A user can hold only one live record (BOOKED or SCHEDULED) per room and slot.
        // $in inside a partial filter needs MongoDB 6.0+
        @CompoundIndex(
                name = "uniq_user_room_slot_live",
                def = "{ 'roomId': 1, 'slotStart': 1, 'userId': 1 }",
                unique = true,
                partialFilter = "{ 'status': { '$in': ['BOOKED', 'SCHEDULED'] } }"),

        // Performance: grid query
        @CompoundIndex(name = "idx_slot_status", def = "{ 'slotStart': 1, 'status': 1 }"),

        // Performance: user's active room and own reservations
        @CompoundIndex(name = "idx_user_status_slot", def = "{ 'userId': 1, 'status': 1, 'slotStart': 1 }"),

        // Performance: Phase 4 room 24h timeline range queries
        @CompoundIndex(name = "idx_room_slot", def = "{ 'roomId': 1, 'slotStart': 1 }"),

        // Performance: Phase 4 user cap and upcoming reservations range count
        @CompoundIndex(name = "idx_user_slot_status", def = "{ 'userId': 1, 'slotStart': 1, 'status': 1 }")
})
public class Reservation {

    @Id
    private String id;

    // Plain Strings everywhere (never ObjectId), so queries always match
    private String roomId;
    private String userId;

    // UTC, on 5-minute boundaries with seconds and nanos zeroed
    private Instant slotStart;
    private Instant slotEnd;

    private ReservationStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}