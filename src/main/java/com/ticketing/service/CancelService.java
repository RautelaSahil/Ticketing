package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.CancelReservationResponse;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.UserPrincipal;
import com.ticketing.util.TimeSlotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelService {

    private final MongoOperations mongoOperations;
    private final ReservationRepository reservationRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    /**
     * Atomically cancels a reservation (BOOKED or SCHEDULED) if more than 10 minutes
     * remain before slot start.
     * Uses find-and-modify with return-original to capture previousStatus for Phase 5 claim logic.
     */
    public CancelReservationResponse cancelReservation(String reservationId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("User must be authenticated to cancel a reservation");
        }

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Reservation ID must not be blank");
        }

        Instant now = Instant.now(clock);
        Duration cutoff = bookingProperties.getCancellationCutoffDuration();
        // Strict > 10m rule: slotStart > now + cutoff
        Instant cutoffThreshold = now.plus(cutoff);

        // 1. Atomic conditional find-and-modify
        Query query = new Query(Criteria.where("_id").is(reservationId)
                .and("userId").is(currentUser.getId())
                .and("status").in(List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED))
                .and("slotStart").gt(cutoffThreshold));

        Update update = new Update()
                .set("status", ReservationStatus.CANCELLED)
                .set("updatedAt", now);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(false); // return original document

        Reservation modified = mongoOperations.findAndModify(query, update, options, Reservation.class);

        if (modified != null) {
            log.info("User '{}' cancelled reservation '{}' (previous status: {})",
                    currentUser.getUsername(), reservationId, modified.getStatus());
            return CancelReservationResponse.builder()
                    .reservationId(reservationId)
                    .previousStatus(modified.getStatus())
                    .cancelledAt(now)
                    .message("Reservation successfully cancelled")
                    .build();
        }

        // 2. Zero matches: run a read-only follow-up query to report the exact cause
        return handleCancelFailure(reservationId, currentUser, now, cutoff);
    }

    private CancelReservationResponse handleCancelFailure(String reservationId, UserPrincipal currentUser, Instant now, Duration cutoff) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);

        if (res == null) {
            throw new ResourceNotFoundException("Reservation not found with ID: " + reservationId);
        }

        if (!currentUser.getId().equals(res.getUserId())) {
            // Do not leak ownership details to other users
            throw new ResourceNotFoundException("Reservation not found with ID: " + reservationId);
        }

        if (res.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalArgumentException("Reservation is already cancelled");
        }

        if (res.getStatus() == ReservationStatus.EXPIRED || res.getStatus() == ReservationStatus.FAILED) {
            throw new IllegalArgumentException("Cannot cancel an ended or failed reservation");
        }

        if (!TimeSlotUtils.isCancellationAllowed(res.getSlotStart(), now, cutoff)) {
            throw new IllegalArgumentException("Cannot cancel within "
                    + bookingProperties.getCancellationCutoffMinutes() + " minutes of the slot start time");
        }

        // Catch-all
        throw new IllegalArgumentException("Reservation cannot be cancelled in its current state: " + res.getStatus());
    }
}
