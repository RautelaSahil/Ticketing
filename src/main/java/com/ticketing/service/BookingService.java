package com.ticketing.service;

import com.ticketing.dto.BookRoomRequest;
import com.ticketing.dto.ReservationResponse;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final Clock clock;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    public ReservationResponse bookRoom(BookRoomRequest request, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadCredentialsException("Authentication is required to book a room");
        }

        if (request == null || request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be empty");
        }

        if (request.getSlotStart() == null) {
            throw new IllegalArgumentException("Slot start time cannot be empty");
        }

        Instant now = clock.instant();

        // 1. Zero-trust interval validation
        if (!TimeSlotUtils.isValidNextInterval(request.getSlotStart(), now)) {
            Instant validNext = TimeSlotUtils.getNextIntervalStart(now);
            throw new IllegalArgumentException(
                    "Stale or invalid time slot. Current next interval start is " + validNext
            );
        }

        // 2. Room existence and active check
        Room room = roomRepository.findById(request.getRoomId())
                .filter(r -> r.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found or is currently inactive: " + request.getRoomId()
                ));

        // 3. Pre-check: prevent self-duplicate reservation
        boolean alreadyReserved = reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                currentUser.getId(),
                room.getId(),
                request.getSlotStart(),
                List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
        );

        if (alreadyReserved) {
            throw new ResourceConflictException("You have already reserved this room for this interval");
        }

        // 4. Construct reservation
        Reservation reservation = Reservation.builder()
                .roomId(room.getId())
                .userId(currentUser.getId())
                .slotStart(request.getSlotStart())
                .slotEnd(TimeSlotUtils.getIntervalEnd(request.getSlotStart()))
                .status(ReservationStatus.BOOKED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 5. Persist with race-condition handling
        try {
            Reservation saved = reservationRepository.save(reservation);
            log.info("User '{}' booked room '{}' for slot {}",
                    currentUser.getUsername(), room.getRoomNumber(), request.getSlotStart());

            return ReservationResponse.builder()
                    .reservationId(saved.getId())
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .slotStart(saved.getSlotStart())
                    .slotEnd(saved.getSlotEnd())
                    .status(saved.getStatus())
                    .createdAt(saved.getCreatedAt())
                    .build();

        } catch (DuplicateKeyException e) {
            log.warn("Race collision: room {} was just booked by another user for slot {}",
                    room.getId(), request.getSlotStart());
            throw new ResourceConflictException("Room was just booked by another user. Please select another room.");
        }
    }
}
