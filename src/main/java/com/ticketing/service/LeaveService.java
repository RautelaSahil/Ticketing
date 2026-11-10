package com.ticketing.service;

import com.mongodb.client.result.UpdateResult;
import com.ticketing.dto.LeaveResponse;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final Clock clock;
    private final MongoOperations mongoOperations;

    public LeaveResponse leaveRoom(String reservationId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadCredentialsException("Authentication is required to leave a room");
        }

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Reservation ID cannot be empty");
        }

        Instant now = clock.instant();

        Query query = new Query(
                Criteria.where("id").is(reservationId)
                        .and("userId").is(currentUser.getId())
                        .and("status").is(ReservationStatus.BOOKED)
                        .and("slotStart").lte(now)
                        .and("slotEnd").gt(now)
        );

        Update update = new Update()
                .set("status", ReservationStatus.CANCELLED)
                .set("updatedAt", now);

        UpdateResult result = mongoOperations.updateFirst(query, update, Reservation.class);

        if (result.getModifiedCount() == 0) {
            log.warn("User '{}' attempted to leave reservation '{}', but no active match found at {}",
                    currentUser.getUsername(), reservationId, now);
            throw new IllegalArgumentException("No active reservation found to release");
        }

        log.info("User '{}' successfully vacated reservation '{}' at {}",
                currentUser.getUsername(), reservationId, now);

        return LeaveResponse.builder()
                .message("Room vacated successfully")
                .reservationId(reservationId)
                .status(ReservationStatus.CANCELLED)
                .build();
    }
}
