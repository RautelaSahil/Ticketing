package com.ticketing.service;

import com.ticketing.dto.GridResponse;
import com.ticketing.dto.RoomGridItemDto;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.model.Room;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.repository.RoomRepository;
import com.ticketing.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GridServiceTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:01:30.000Z");
    private final Instant expectedNextStart = Instant.parse("2026-11-10T12:05:00.000Z");
    private final Instant expectedNextEnd = Instant.parse("2026-11-10T12:10:00.000Z");

    private List<Room> mockRooms;
    private List<Reservation> mockBookedReservations;
    private GridService gridService;

    @BeforeEach
    void setUp() {
        mockRooms = new ArrayList<>();
        mockBookedReservations = new ArrayList<>();

        RoomRepository roomRepository = (RoomRepository) Proxy.newProxyInstance(
                RoomRepository.class.getClassLoader(),
                new Class<?>[]{RoomRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByActiveTrueOrderByRoomNumberAsc")) {
                        return mockRooms;
                    }
                    return null;
                }
        );

        ReservationRepository reservationRepository = (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class<?>[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findBySlotStartAndStatus")) {
                        return mockBookedReservations;
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        gridService = new GridService(fixedClock, roomRepository, reservationRepository);

        mockRooms.add(Room.builder()
                .id("room-101-id")
                .roomNumber("101")
                .capacity(4)
                .metadata(Map.of("floor", 1))
                .active(true)
                .build());

        mockRooms.add(Room.builder()
                .id("room-102-id")
                .roomNumber("102")
                .capacity(6)
                .metadata(Map.of("floor", 1))
                .active(true)
                .build());

        mockRooms.add(Room.builder()
                .id("room-103-id")
                .roomNumber("103")
                .capacity(2)
                .metadata(Map.of("floor", 1))
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Should return all rooms available when no reservations exist for next interval")
    void shouldReturnAllAvailableRooms() {
        UserPrincipal currentUser = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));
        GridResponse response = gridService.getNextIntervalGrid(currentUser);

        assertThat(response.getServerTime()).isEqualTo(fixedNow);
        assertThat(response.getSlotStart()).isEqualTo(expectedNextStart);
        assertThat(response.getSlotEnd()).isEqualTo(expectedNextEnd);
        assertThat(response.getRooms()).hasSize(3);

        assertThat(response.getRooms()).allMatch(r -> !r.isOccupied() && !r.isYours());
    }

    @Test
    @DisplayName("Should mark occupied and yours flags accurately based on current user")
    void shouldDifferentiateYoursAndOtherUserBookings() {
        Reservation bookingAlice = Reservation.builder()
                .id("res-1")
                .roomId("room-101-id")
                .userId("user-alice")
                .slotStart(expectedNextStart)
                .slotEnd(expectedNextEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        Reservation bookingBob = Reservation.builder()
                .id("res-2")
                .roomId("room-102-id")
                .userId("user-bob")
                .slotStart(expectedNextStart)
                .slotEnd(expectedNextEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        mockBookedReservations.add(bookingAlice);
        mockBookedReservations.add(bookingBob);

        UserPrincipal alicePrincipal = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));
        GridResponse response = gridService.getNextIntervalGrid(alicePrincipal);

        List<RoomGridItemDto> rooms = response.getRooms();
        assertThat(rooms).hasSize(3);

        // Room 101 booked by Alice (occupied=true, yours=true)
        RoomGridItemDto r101 = rooms.stream().filter(r -> r.getRoomNumber().equals("101")).findFirst().orElseThrow();
        assertThat(r101.isOccupied()).isTrue();
        assertThat(r101.isYours()).isTrue();

        // Room 102 booked by Bob (occupied=true, yours=false)
        RoomGridItemDto r102 = rooms.stream().filter(r -> r.getRoomNumber().equals("102")).findFirst().orElseThrow();
        assertThat(r102.isOccupied()).isTrue();
        assertThat(r102.isYours()).isFalse();

        // Room 103 unbooked (occupied=false, yours=false)
        RoomGridItemDto r103 = rooms.stream().filter(r -> r.getRoomNumber().equals("103")).findFirst().orElseThrow();
        assertThat(r103.isOccupied()).isFalse();
        assertThat(r103.isYours()).isFalse();
    }

    @Test
    @DisplayName("Should safely handle anonymous/null currentUser without errors")
    void shouldHandleNullCurrentUser() {
        Reservation bookingBob = Reservation.builder()
                .id("res-2")
                .roomId("room-101-id")
                .userId("user-bob")
                .slotStart(expectedNextStart)
                .slotEnd(expectedNextEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        mockBookedReservations.add(bookingBob);

        GridResponse response = gridService.getNextIntervalGrid(null);

        assertThat(response.getRooms()).hasSize(3);
        RoomGridItemDto r101 = response.getRooms().stream().filter(r -> r.getRoomNumber().equals("101")).findFirst().orElseThrow();
        assertThat(r101.isOccupied()).isTrue();
        assertThat(r101.isYours()).isFalse();
    }
}
