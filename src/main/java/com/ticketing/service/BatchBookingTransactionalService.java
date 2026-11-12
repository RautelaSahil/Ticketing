package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.BatchBookingResponse;
import com.ticketing.dto.BookBatchRequest;
import com.ticketing.dto.ReservationResponse;
import com.ticketing.exception.BatchBookingConflictException;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transactional execution component for Phase 3 batch bookings.
 * Runs strictly within a MongoDB multi-document ACID transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchBookingTransactionalService {

    private final Clock clock;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final BookingProperties bookingProperties;

    @Transactional(rollbackFor = Exception.class)
    public BatchBookingResponse executeBatchBooking(UserPrincipal currentUser, BookBatchRequest request) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BadCredentialsException("Authentication is required to book rooms");
        }

        if (request == null || request.getSlotStart() == null) {
            throw new IllegalArgumentException("Slot start time cannot be empty");
        }

        Instant now = clock.instant();

        // 1. Zero-trust interval validation (must match immediate next interval)
        if (!TimeSlotUtils.isValidNextInterval(request.getSlotStart(), now)) {
            Instant validNext = TimeSlotUtils.getNextIntervalStart(now);
            throw new IllegalArgumentException(
                    "Stale or invalid time slot. Current next interval start is " + validNext
            );
        }

        // 2. Validate room list and cap (empty -> 400, > max -> 400, duplicates removed)
        if (request.getRoomIds() == null || request.getRoomIds().isEmpty()) {
            throw new IllegalArgumentException("Room selection cannot be empty");
        }

        List<String> uniqueRoomIds = request.getRoomIds().stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();

        if (uniqueRoomIds.isEmpty()) {
            throw new IllegalArgumentException("Room selection cannot be empty");
        }

        int maxAllowed = bookingProperties.getMaxRoomsPerRequest();
        if (uniqueRoomIds.size() > maxAllowed) {
            throw new IllegalArgumentException(
                    "Cannot book more than " + maxAllowed + " rooms per request (requested: " + uniqueRoomIds.size() + ")"
            );
        }

        // 3. Load rooms and verify existence + active state (missing or inactive -> 404)
        List<Room> loadedRooms = roomRepository.findAllById(uniqueRoomIds);
        Map<String, Room> roomsById = loadedRooms.stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r));

        List<String> invalidRoomIds = new ArrayList<>();
        for (String id : uniqueRoomIds) {
            Room room = roomsById.get(id);
            if (room == null || !room.isActive()) {
                invalidRoomIds.add(id);
            }
        }

        if (!invalidRoomIds.isEmpty()) {
            throw new ResourceNotFoundException(
                    "One or more rooms were not found or are inactive: " + invalidRoomIds
            );
        }

        // 4. Pre-check: User must not already hold any requested room for this slot
        List<Reservation> userExisting = reservationRepository.findByUserIdAndRoomIdInAndSlotStartAndStatusIn(
                currentUser.getId(),
                uniqueRoomIds,
                request.getSlotStart(),
                List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
        );

        if (!userExisting.isEmpty()) {
            List<String> selfConflictedNumbers = userExisting.stream()
                    .map(r -> {
                        Room rm = roomsById.get(r.getRoomId());
                        return rm != null ? rm.getRoomNumber() : r.getRoomId();
                    })
                    .distinct()
                    .sorted()
                    .toList();
            throw new ResourceConflictException(
                    "You already hold a reservation for room(s): " + selfConflictedNumbers
            );
        }

        // 5. Pre-check: Identify any rooms already BOOKED by others for this slot
        List<Reservation> existingBookings = reservationRepository.findByRoomIdInAndSlotStartAndStatus(
                uniqueRoomIds,
                request.getSlotStart(),
                ReservationStatus.BOOKED
        );

        if (!existingBookings.isEmpty()) {
            List<String> unavailableRoomNumbers = existingBookings.stream()
                    .map(r -> {
                        Room rm = roomsById.get(r.getRoomId());
                        return rm != null ? rm.getRoomNumber() : r.getRoomId();
                    })
                    .distinct()
                    .sorted()
                    .toList();
            throw new BatchBookingConflictException(
                    "One or more selected rooms are unavailable. No reservations were made.",
                    unavailableRoomNumbers
            );
        }

        // 6. Deterministic sorting by roomId to prevent database deadlocks
        List<String> sortedRoomIds = uniqueRoomIds.stream().sorted().toList();

        // 7. Atomic batch creation
        Instant slotEnd = TimeSlotUtils.getIntervalEnd(request.getSlotStart());
        List<Reservation> reservationsToSave = new ArrayList<>();
        for (String roomId : sortedRoomIds) {
            reservationsToSave.add(Reservation.builder()
                    .roomId(roomId)
                    .userId(currentUser.getId())
                    .slotStart(request.getSlotStart())
                    .slotEnd(slotEnd)
                    .status(ReservationStatus.BOOKED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        List<Reservation> saved = reservationRepository.saveAll(reservationsToSave);
        log.info("User '{}' successfully booked batch of {} room(s) for slot {}",
                currentUser.getUsername(), saved.size(), request.getSlotStart());

        List<ReservationResponse> responses = saved.stream()
                .map(r -> {
                    Room room = roomsById.get(r.getRoomId());
                    return ReservationResponse.builder()
                            .reservationId(r.getId())
                            .roomId(r.getRoomId())
                            .roomNumber(room != null ? room.getRoomNumber() : "Unknown")
                            .slotStart(r.getSlotStart())
                            .slotEnd(r.getSlotEnd())
                            .status(r.getStatus())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();

        return BatchBookingResponse.builder()
                .slotStart(request.getSlotStart())
                .slotEnd(slotEnd)
                .reservations(responses)
                .message("Successfully reserved " + responses.size() + " room(s)")
                .build();
    }
}
