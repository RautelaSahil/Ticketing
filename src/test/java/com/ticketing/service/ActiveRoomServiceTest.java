package com.ticketing.service;

import com.ticketing.dto.ActiveReservationDto;
import com.ticketing.dto.ActiveRoomsResponse;
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

class ActiveRoomServiceTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:02:15.000Z");
    private final Instant activeSlotStart = Instant.parse("2026-11-10T12:00:00.000Z");
    private final Instant activeSlotEnd = Instant.parse("2026-11-10T12:05:00.000Z");

    private List<Reservation> mockActiveBookings;
    private List<Room> mockRooms;
    private ActiveRoomService activeRoomService;
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        mockActiveBookings = new ArrayList<>();
        mockRooms = new ArrayList<>();

        RoomRepository roomRepository = (RoomRepository) Proxy.newProxyInstance(
                RoomRepository.class.getClassLoader(),
                new Class<?>[]{RoomRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAllById")) {
                        return mockRooms;
                    }
                    return null;
                }
        );

        ReservationRepository reservationRepository = (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class<?>[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findActiveBookedByUser")) {
                        return mockActiveBookings;
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        activeRoomService = new ActiveRoomService(fixedClock, reservationRepository, roomRepository);

        alice = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));

        mockRooms.add(Room.builder()
                .id("room-101-id")
                .roomNumber("101")
                .capacity(4)
                .metadata(Map.of("floor", 1, "hasProjector", true))
                .active(true)
                .build());

        mockRooms.add(Room.builder()
                .id("room-102-id")
                .roomNumber("102")
                .capacity(6)
                .metadata(Map.of("floor", 1))
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Should return empty list when user has no active room bookings")
    void shouldReturnEmptyListWhenNoActiveBookings() {
        ActiveRoomsResponse response = activeRoomService.getMyActiveRooms(alice);

        assertThat(response).isNotNull();
        assertThat(response.getServerTime()).isEqualTo(fixedNow);
        assertThat(response.getActiveReservations()).isEmpty();
    }

    @Test
    @DisplayName("Should return active room with accurate remaining countdown seconds during active interval")
    void shouldReturnActiveRoomWithCountdown() {
        Reservation activeBooking = Reservation.builder()
                .id("res-active-1")
                .roomId("room-101-id")
                .userId("user-alice")
                .slotStart(activeSlotStart)
                .slotEnd(activeSlotEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        mockActiveBookings.add(activeBooking);

        ActiveRoomsResponse response = activeRoomService.getMyActiveRooms(alice);

        assertThat(response).isNotNull();
        assertThat(response.getServerTime()).isEqualTo(fixedNow);
        assertThat(response.getActiveReservations()).hasSize(1);

        ActiveReservationDto dto = response.getActiveReservations().get(0);
        assertThat(dto.getReservationId()).isEqualTo("res-active-1");
        assertThat(dto.getRoomId()).isEqualTo("room-101-id");
        assertThat(dto.getRoomNumber()).isEqualTo("101");
        assertThat(dto.getCapacity()).isEqualTo(4);
        assertThat(dto.getMetadata()).containsEntry("floor", 1);
        assertThat(dto.getSlotStart()).isEqualTo(activeSlotStart);
        assertThat(dto.getSlotEnd()).isEqualTo(activeSlotEnd);

        // 12:05:00 - 12:02:15 = 165 seconds remaining
        assertThat(dto.getRemainingSeconds()).isEqualTo(165L);
    }

    @Test
    @DisplayName("Should support multiple active rooms for the same user in the same interval")
    void shouldReturnMultipleActiveRooms() {
        Reservation res1 = Reservation.builder()
                .id("res-1")
                .roomId("room-101-id")
                .userId("user-alice")
                .slotStart(activeSlotStart)
                .slotEnd(activeSlotEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        Reservation res2 = Reservation.builder()
                .id("res-2")
                .roomId("room-102-id")
                .userId("user-alice")
                .slotStart(activeSlotStart)
                .slotEnd(activeSlotEnd)
                .status(ReservationStatus.BOOKED)
                .build();

        mockActiveBookings.add(res1);
        mockActiveBookings.add(res2);

        ActiveRoomsResponse response = activeRoomService.getMyActiveRooms(alice);

        assertThat(response.getActiveReservations()).hasSize(2);
        List<String> roomNumbers = new ArrayList<>();
        for (ActiveReservationDto dto : response.getActiveReservations()) {
            roomNumbers.add(dto.getRoomNumber());
        }
        assertThat(roomNumbers).containsExactlyInAnyOrder("101", "102");
    }

    @Test
    @DisplayName("Should safely return empty list when currentUser is null")
    void shouldHandleNullCurrentUser() {
        ActiveRoomsResponse response = activeRoomService.getMyActiveRooms(null);

        assertThat(response).isNotNull();
        assertThat(response.getServerTime()).isEqualTo(fixedNow);
        assertThat(response.getActiveReservations()).isEmpty();
    }
}
