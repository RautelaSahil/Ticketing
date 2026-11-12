package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.FutureBookRequest;
import com.ticketing.dto.FutureBookingResponse;
import com.ticketing.exception.ResourceConflictException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.model.Room;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.repository.RoomRepository;
import com.ticketing.security.UserPrincipal;
import com.ticketing.util.TimeSlotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FutureBookingService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public FutureBookingResponse reserveFutureSlot(FutureBookRequest request, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("User must be authenticated to reserve a room");
        }

        if (request == null || request.getRoomId() == null || request.getSlotStart() == null) {
            throw new IllegalArgumentException("Room ID and slot start time must not be null");
        }

        Instant slotStart = request.getSlotStart();
        Instant now = Instant.now(clock);
        Duration windowDuration = bookingProperties.getFutureWindowDuration();
        Duration cutoffDuration = bookingProperties.getCancellationCutoffDuration();

        // 1. Validate slotStart is on 5-minute UTC boundary
        if (!TimeSlotUtils.isOnBoundary(slotStart)) {
            throw new IllegalArgumentException("Slot start time must be on a 5-minute boundary with zero seconds and nanos: " + slotStart);
        }

        // 2. Validate slotStart is within future booking window [T_next, now + windowDuration]
        if (!TimeSlotUtils.isWithinFutureWindow(slotStart, now, windowDuration)) {
            throw new IllegalArgumentException("Slot start time must be within the booking window (from next interval up to "
                    + bookingProperties.getFutureWindowHours() + " hours ahead): " + slotStart);
        }

        // 3. Verify room exists and is active
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + request.getRoomId()));
        if (!room.isActive()) {
            throw new ResourceNotFoundException("Room is inactive: " + request.getRoomId());
        }

        // 4. Check user cap: max upcoming live reservations (BOOKED + SCHEDULED)
        long currentUpcomingCount = reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                currentUser.getId(),
                now,
                List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
        );
        if (currentUpcomingCount >= bookingProperties.getMaxFutureReservationsPerUser()) {
            throw new ResourceConflictException("You have reached your limit of "
                    + bookingProperties.getMaxFutureReservationsPerUser() + " upcoming reservations");
        }

        // 5. Pre-check for duplicate reservation by this user
        boolean alreadyReservedByUser = reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                currentUser.getId(),
                room.getId(),
                slotStart,
                List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
        );
        if (alreadyReservedByUser) {
            throw new ResourceConflictException("You have already reserved this room for this interval");
        }

        Instant slotEnd = slotStart.plus(TimeSlotUtils.INTERVAL_DURATION);

        // 6. Attempt direct insert as BOOKED
        Reservation bookedAttempt = Reservation.builder()
                .roomId(room.getId())
                .userId(currentUser.getId())
                .slotStart(slotStart)
                .slotEnd(slotEnd)
                .status(ReservationStatus.BOOKED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            Reservation saved = reservationRepository.insert(bookedAttempt);
            log.info("User '{}' directly BOOKED room '{}' for slot {}", currentUser.getUsername(), room.getRoomNumber(), slotStart);
            return FutureBookingResponse.builder()
                    .reservationId(saved.getId())
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .slotStart(slotStart)
                    .slotEnd(slotEnd)
                    .status(ReservationStatus.BOOKED)
                    .message("Reservation confirmed")
                    .build();
        } catch (DuplicateKeyException e) {
            // Index uniq_booked_room_slot or uniq_user_room_slot_live rejected the insert
            // Check if user already holds a live record
            if (reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                    currentUser.getId(), room.getId(), slotStart,
                    List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED))) {
                throw new ResourceConflictException("You have already reserved this room for this interval");
            }

            // Another user holds the BOOKED slot. Apply the waiting list queue rule
            boolean canQueue = TimeSlotUtils.canJoinQueue(slotStart, now, cutoffDuration);
            if (!canQueue) {
                log.warn("User '{}' denied queueing for room '{}' at {} because <= 10m remain",
                        currentUser.getUsername(), room.getRoomNumber(), slotStart);
                throw new ResourceConflictException("This slot is already taken and cannot be queued within 10 minutes of start");
            }

            // More than 10 minutes remain -> automatically place into SCHEDULED queue
            Reservation scheduledAttempt = Reservation.builder()
                    .roomId(room.getId())
                    .userId(currentUser.getId())
                    .slotStart(slotStart)
                    .slotEnd(slotEnd)
                    .status(ReservationStatus.SCHEDULED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            try {
                Reservation savedScheduled = reservationRepository.insert(scheduledAttempt);
                log.info("User '{}' queued as SCHEDULED for room '{}' for slot {}",
                        currentUser.getUsername(), room.getRoomNumber(), slotStart);
                return FutureBookingResponse.builder()
                        .reservationId(savedScheduled.getId())
                        .roomId(room.getId())
                        .roomNumber(room.getRoomNumber())
                        .slotStart(slotStart)
                        .slotEnd(slotEnd)
                        .status(ReservationStatus.SCHEDULED)
                        .message("Room is currently held. You have joined the waiting list.")
                        .build();
            } catch (DuplicateKeyException scheduledDupEx) {
                // In case the user simultaneously inserted SCHEDULED
                throw new ResourceConflictException("You have already reserved this room for this interval");
            }
        }
    }
}
