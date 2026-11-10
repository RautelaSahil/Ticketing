package com.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndexes({
        // Idempotency: a failure notice is sent at most once per user, room and slot.
        // Partial on SCHEDULE_FAILURE so repeated CLAIM_OPPORTUNITY rounds are not blocked.
        @CompoundIndex(
                name = "uniq_failure_once",
                def = "{ 'userId': 1, 'roomId': 1, 'slotStart': 1, 'type': 1 }",
                unique = true,
                partialFilter = "{ 'type': 'SCHEDULE_FAILURE' }"),

        // Inbox: all notifications for a user, newest first
        @CompoundIndex(name = "idx_user_created", def = "{ 'userId': 1, 'createdAt': -1 }"),

        // Active claims lookup
        @CompoundIndex(
                name = "idx_user_type_status_expires",
                def = "{ 'userId': 1, 'type': 1, 'status': 1, 'expiresAt': 1 }"),

        // Round invalidation
        @CompoundIndex(name = "idx_round_status", def = "{ 'roundId': 1, 'status': 1 }")
})
public class Notification {

    @Id
    private String id;

    // Plain Strings everywhere (never ObjectId), so queries always match
    private String userId;
    private String roomId;

    // UTC, on a 5-minute boundary, same value as Reservation.slotStart
    private Instant slotStart;

    private NotificationType type;

    // Claims: PENDING -> CLAIMED or EXPIRED.
    // Failure notices: PENDING (unread) -> READ.
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    // String, not UUID, to avoid driver UUID-representation mismatches.
    // Null for SCHEDULE_FAILURE.
    private String roundId;

    private Instant createdAt;

    // Set for claims (at most slotStart minus 10 minutes). Null for failure notices.
    private Instant expiresAt;
}