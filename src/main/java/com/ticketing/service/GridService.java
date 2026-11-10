package com.ticketing.service;

import com.ticketing.dto.GridResponse;
import com.ticketing.dto.RoomGridItemDto;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GridService {

    private final Clock clock;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    public GridResponse getNextIntervalGrid(UserPrincipal currentUser) {
        Instant now = clock.instant();
        Instant slotStart = TimeSlotUtils.getNextIntervalStart(now);
        Instant slotEnd = TimeSlotUtils.getIntervalEnd(slotStart);

        List<Room> activeRooms = roomRepository.findByActiveTrueOrderByRoomNumberAsc();
        List<Reservation> bookedReservations = reservationRepository.findBySlotStartAndStatus(
                slotStart, ReservationStatus.BOOKED
        );

        Map<String, Reservation> bookedByRoomId = bookedReservations.stream()
                .collect(Collectors.toMap(r -> r.getRoomId(), r -> r, (r1, r2) -> r1));

        String currentUserId = currentUser != null ? currentUser.getId() : null;

        List<RoomGridItemDto> roomDtos = activeRooms.stream()
                .map(room -> {
                    Reservation booked = bookedByRoomId.get(room.getId());
                    boolean occupied = (booked != null);
                    boolean yours = (booked != null) && currentUserId != null && currentUserId.equals(booked.getUserId());

                    return RoomGridItemDto.builder()
                            .roomId(room.getId())
                            .roomNumber(room.getRoomNumber())
                            .capacity(room.getCapacity())
                            .metadata(room.getMetadata())
                            .occupied(occupied)
                            .yours(yours)
                            .build();
                })
                .collect(Collectors.toList());

        log.debug("Fetched next-interval grid for slot {}: {} rooms, {} occupied",
                slotStart, roomDtos.size(), bookedReservations.size());

        return GridResponse.builder()
                .serverTime(now)
                .slotStart(slotStart)
                .slotEnd(slotEnd)
                .rooms(roomDtos)
                .build();
    }
}
