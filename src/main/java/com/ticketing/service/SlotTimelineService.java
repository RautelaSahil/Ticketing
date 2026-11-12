package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.SlotAction;
import com.ticketing.dto.SlotTimelineItemDto;
import com.ticketing.dto.SlotTimelineResponse;
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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotTimelineService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public SlotTimelineResponse getTimelineForRoom(String roomId, UserPrincipal currentUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        if (!room.isActive()) {
            throw new ResourceNotFoundException("Room is inactive: " + roomId);
        }

        Instant now = Instant.now(clock);
        Duration windowDuration = bookingProperties.getFutureWindowDuration();
        Duration cutoffDuration = bookingProperties.getCancellationCutoffDuration();

        List<Instant> slotStarts = TimeSlotUtils.generateFutureSlotStarts(now, windowDuration);
        if (slotStarts.isEmpty()) {
            return SlotTimelineResponse.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .serverTime(now)
                    .windowStart(now)
                    .windowEnd(now)
                    .slots(List.of())
                    .build();
        }

        Instant windowStart = slotStarts.get(0);
        Instant windowEnd = slotStarts.get(slotStarts.size() - 1);

        // Fetch all BOOKED and SCHEDULED reservations for this room across window
        List<Reservation> reservations = reservationRepository.findByRoomIdAndSlotStartBetweenAndStatusIn(
                room.getId(),
                windowStart,
                windowEnd,
                List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
        );

        // Group by slotStart
        Map<Instant, List<Reservation>> bySlot = reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getSlotStart()));

        String currentUserId = (currentUser != null) ? currentUser.getId() : null;

        List<SlotTimelineItemDto> slotItems = new ArrayList<>(slotStarts.size());

        for (Instant slotStart : slotStarts) {
            Instant slotEnd = slotStart.plus(TimeSlotUtils.INTERVAL_DURATION);
            List<Reservation> slotReservations = bySlot.getOrDefault(slotStart, List.of());

            Reservation bookedRes = null;
            long scheduledCount = 0;
            boolean userIsBooked = false;
            boolean userIsScheduled = false;

            for (Reservation r : slotReservations) {
                if (r.getStatus() == ReservationStatus.BOOKED) {
                    bookedRes = r;
                    if (currentUserId != null && currentUserId.equals(r.getUserId())) {
                        userIsBooked = true;
                    }
                } else if (r.getStatus() == ReservationStatus.SCHEDULED) {
                    scheduledCount++;
                    if (currentUserId != null && currentUserId.equals(r.getUserId())) {
                        userIsScheduled = true;
                    }
                }
            }

            SlotAction action;
            boolean cancellable = false;
            Instant cancellationDeadline = slotStart.minus(cutoffDuration);

            if (userIsBooked) {
                action = SlotAction.YOURS_BOOKED;
                cancellable = TimeSlotUtils.isCancellationAllowed(slotStart, now, cutoffDuration);
            } else if (userIsScheduled) {
                action = SlotAction.YOURS_SCHEDULED;
                cancellable = TimeSlotUtils.isCancellationAllowed(slotStart, now, cutoffDuration);
            } else if (bookedRes == null) {
                // Free slot!
                action = SlotAction.BOOK;
            } else {
                // Held by someone else
                boolean canQueue = TimeSlotUtils.canJoinQueue(slotStart, now, cutoffDuration);
                if (canQueue) {
                    action = SlotAction.JOIN_QUEUE;
                } else {
                    action = SlotAction.UNAVAILABLE;
                }
            }

            slotItems.add(SlotTimelineItemDto.builder()
                    .slotStart(slotStart)
                    .slotEnd(slotEnd)
                    .action(action)
                    .waitingCount(scheduledCount)
                    .cancellable(cancellable)
                    .cancellationDeadline(cancellationDeadline)
                    .build());
        }

        return SlotTimelineResponse.builder()
                .roomId(room.getId())
                .roomNumber(room.getRoomNumber())
                .serverTime(now)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .slots(slotItems)
                .build();
    }
}
