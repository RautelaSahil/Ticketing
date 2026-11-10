package com.ticketing.repository;

import com.ticketing.model.Notification;
import com.ticketing.model.NotificationStatus;
import com.ticketing.model.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    // Phase 6 inbox: both types, newest first. Add a Pageable version once inboxes grow.
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    // Phase 6 active claims. The expiresAt filter makes expiry correct without any job,
    // and the type filter keeps failure notices out of the result.
    @Query(value = "{ 'userId': ?0, 'type': 'CLAIM_OPPORTUNITY', 'status': 'PENDING', "
            + "'expiresAt': { '$gt': ?1 } }",
            sort = "{ 'expiresAt': 1 }")
    List<Notification> findActiveClaims(String userId, Instant now);

    // Reads and tests only. The real bulk invalidation (a single update inside the
    // winning claim's transaction) comes with the MongoTemplate fragment in Phase 5.
    List<Notification> findByRoundIdAndStatus(String roundId, NotificationStatus status);

    // Friendly pre-check for failure notices. The worker should still insert and treat
    // a duplicate-key error as "already sent", since check-then-insert can race.
    boolean existsByUserIdAndRoomIdAndSlotStartAndType(
            String userId, String roomId, Instant slotStart, NotificationType type);
}