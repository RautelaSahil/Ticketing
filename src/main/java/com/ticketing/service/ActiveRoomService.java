package com.ticketing.service;

import com.ticketing.dto.ActiveReservationDto;
import com.ticketing.dto.ActiveRoomsResponse;
import com.ticketing.model.Reservation;
import com.ticketing.model.Room;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.repository.RoomRepository;
import com.ticketing.security.UserPrincipal;
import com.ticketing.util.TimeSlotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveRoomService {

    private final Clock clock;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    public ActiveRoomsResponse getMyActiveRooms(UserPrincipal currentUser) {
        Instant now = clock.instant();

        if (currentUser == null) {
            return ActiveRoomsResponse.builder()
                    .serverTime(now)
                    .activeReservations(List.of())
                    .build();
        }

        List<Reservation> activeBookings = reservationRepository.findActiveBookedByUser(
                currentUser.getId(), now
        );

        if (activeBookings.isEmpty()) {
            return ActiveRoomsResponse.builder()
                    .serverTime(now)
                    .activeReservations(List.of())
                    .build();
        }

        Set<String> roomIds = activeBookings.stream()
                .map(res -> res.getRoomId())
                .collect(Collectors.toSet());

        Map<String, Room> roomMap = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r));

        List<ActiveReservationDto> dtos = activeBookings.stream()
                .map(res -> {
                    Room room = roomMap.get(res.getRoomId());
                    long remainingSeconds = TimeSlotUtils.getRemainingSeconds(res.getSlotEnd(), now);

                    return ActiveReservationDto.builder()
                            .reservationId(res.getId())
                            .roomId(res.getRoomId())
                            .roomNumber(room != null ? room.getRoomNumber() : "Unknown")
                            .capacity(room != null ? room.getCapacity() : 0)
                            .metadata(room != null ? room.getMetadata() : Map.of())
                            .slotStart(res.getSlotStart())
                            .slotEnd(res.getSlotEnd())
                            .remainingSeconds(remainingSeconds)
                            .build();
                })
                .collect(Collectors.toList());

        log.debug("Found {} active room(s) for user '{}' at {}", dtos.size(), currentUser.getUsername(), now);

        return ActiveRoomsResponse.builder()
                .serverTime(now)
                .activeReservations(dtos)
                .build();
    }
}
