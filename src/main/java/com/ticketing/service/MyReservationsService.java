package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.FutureReservationSummaryDto;
import com.ticketing.dto.MyFutureReservationsResponse;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
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
public class MyReservationsService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    public MyFutureReservationsResponse getMyFutureReservations(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("User must be authenticated");
        }

        Instant now = Instant.now(clock);
        Duration cutoff = bookingProperties.getCancellationCutoffDuration();

        // 1. Fetch upcoming BOOKED and SCHEDULED rows where slotStart >= now
        List<Reservation> reservations = reservationRepository
                .findByUserIdAndSlotStartGreaterThanEqualAndStatusInOrderBySlotStartAsc(
                        currentUser.getId(),
                        now,
                        List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)
                );

        if (reservations.isEmpty()) {
            return MyFutureReservationsResponse.builder()
                    .serverTime(now)
                    .totalCount(0)
                    .reservations(List.of())
                    .build();
        }

        // 2. Fetch room details
        List<String> roomIds = reservations.stream()
                .map(r -> r.getRoomId())
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> roomNumberMap = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r.getRoomNumber()));

        // 3. Map to summary DTO with server-computed cancellable flag and cancellationDeadline
        List<FutureReservationSummaryDto> dtoList = new ArrayList<>(reservations.size());
        for (Reservation r : reservations) {
            boolean cancellable = TimeSlotUtils.isCancellationAllowed(r.getSlotStart(), now, cutoff);
            Instant deadline = r.getSlotStart().minus(cutoff);
            String roomNumber = roomNumberMap.getOrDefault(r.getRoomId(), "Unknown");

            dtoList.add(FutureReservationSummaryDto.builder()
                    .reservationId(r.getId())
                    .roomId(r.getRoomId())
                    .roomNumber(roomNumber)
                    .slotStart(r.getSlotStart())
                    .slotEnd(r.getSlotEnd())
                    .status(r.getStatus())
                    .cancellable(cancellable)
                    .cancellationDeadline(deadline)
                    .createdAt(r.getCreatedAt())
                    .build());
        }

        return MyFutureReservationsResponse.builder()
                .serverTime(now)
                .totalCount(dtoList.size())
                .reservations(dtoList)
                .build();
    }
}
