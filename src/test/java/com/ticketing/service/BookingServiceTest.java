package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.BookRoomRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingServiceTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:01:30.000Z");
    private final Instant validNextStart = Instant.parse("2026-11-10T12:05:00.000Z");
    private final Instant expectedNextEnd = Instant.parse("2026-11-10T12:10:00.000Z");

    private Room mockRoom;
    private AtomicBoolean duplicateOnSave;
    private AtomicBoolean alreadyExistsForUser;
    private BookingService bookingService;
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        duplicateOnSave = new AtomicBoolean(false);
        alreadyExistsForUser = new AtomicBoolean(false);

        mockRoom = Room.builder()
                .id("room-101-id")
                .roomNumber("101")
                .capacity(4)
                .metadata(Map.of("floor", 1))
                .active(true)
                .build();

        RoomRepository roomRepository = (RoomRepository) Proxy.newProxyInstance(
                RoomRepository.class.getClassLoader(),
                new Class<?>[]{RoomRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) {
                        String id = (String) args[0];
                        if ("room-101-id".equals(id)) {
                            return Optional.of(mockRoom);
                        }
                        return Optional.empty();
                    }
                    if (method.getName().equals("findAllById")) {
                        @SuppressWarnings("unchecked")
                        Iterable<String> ids = (Iterable<String>) args[0];
                        List<Room> result = new ArrayList<>();
                        for (String id : ids) {
                            if ("room-101-id".equals(id)) {
                                result.add(mockRoom);
                            }
                        }
                        return result;
                    }
                    return null;
                }
        );

        ReservationRepository reservationRepository = (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class<?>[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsByUserIdAndRoomIdAndSlotStartAndStatusIn")) {
                        return alreadyExistsForUser.get();
                    }
                    if (method.getName().equals("findByUserIdAndRoomIdInAndSlotStartAndStatusIn")) {
                        if (alreadyExistsForUser.get()) {
                            return List.of(Reservation.builder()
                                    .id("existing-res")
                                    .roomId("room-101-id")
                                    .userId("user-alice")
                                    .slotStart(validNextStart)
                                    .slotEnd(expectedNextEnd)
                                    .status(ReservationStatus.BOOKED)
                                    .build());
                        }
                        return List.of();
                    }
                    if (method.getName().equals("findByRoomIdInAndSlotStartAndStatus")) {
                        if (duplicateOnSave.get()) {
                            return List.of(Reservation.builder()
                                    .id("taken-res")
                                    .roomId("room-101-id")
                                    .userId("user-bob")
                                    .slotStart(validNextStart)
                                    .slotEnd(expectedNextEnd)
                                    .status(ReservationStatus.BOOKED)
                                    .build());
                        }
                        return List.of();
                    }
                    if (method.getName().equals("saveAll")) {
                        if (duplicateOnSave.get()) {
                            throw new DuplicateKeyException("E11000 duplicate key error on index uniq_booked_room_slot");
                        }
                        @SuppressWarnings("unchecked")
                        Iterable<Reservation> list = (Iterable<Reservation>) args[0];
                        List<Reservation> saved = new ArrayList<>();
                        for (Reservation r : list) {
                            r.setId("res-assigned-123");
                            saved.add(r);
                        }
                        return saved;
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        BatchBookingTransactionalService txService = new BatchBookingTransactionalService(
                fixedClock, roomRepository, reservationRepository, new BookingProperties()
        );
        BatchBookingService batchBookingService = new BatchBookingService(
                txService, reservationRepository, roomRepository
        );
        bookingService = new BookingService(batchBookingService);

        alice = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));
    }

    @Test
    @DisplayName("Should successfully book an available room for the next valid interval via batch pipeline")
    void shouldBookRoomSuccessfully() {
        BookRoomRequest request = BookRoomRequest.builder()
                .roomId("room-101-id")
                .slotStart(validNextStart)
                .build();

        ReservationResponse response = bookingService.bookRoom(request, alice);

        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isEqualTo("res-assigned-123");
        assertThat(response.getRoomId()).isEqualTo("room-101-id");
        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getSlotStart()).isEqualTo(validNextStart);
        assertThat(response.getSlotEnd()).isEqualTo(expectedNextEnd);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        assertThat(response.getCreatedAt()).isEqualTo(fixedNow);
    }

    @Test
    @DisplayName("Should reject stale or invalid slotStart with 400 Bad Request")
    void shouldRejectStaleSlotStart() {
        // 12:00:00 is stale when current time is 12:01:30
        BookRoomRequest staleRequest = BookRoomRequest.builder()
                .roomId("room-101-id")
                .slotStart(Instant.parse("2026-11-10T12:00:00.000Z"))
                .build();

        assertThatThrownBy(() -> bookingService.bookRoom(staleRequest, alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stale or invalid time slot");
    }

    @Test
    @DisplayName("Should reject nonexistent or inactive room with 404 Not Found")
    void shouldRejectMissingRoom() {
        BookRoomRequest request = BookRoomRequest.builder()
                .roomId("nonexistent-room")
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> bookingService.bookRoom(request, alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more rooms were not found or are inactive");
    }

    @Test
    @DisplayName("Should reject duplicate booking attempt by same user with 409 Conflict")
    void shouldRejectDuplicateBookingBySameUser() {
        alreadyExistsForUser.set(true);

        BookRoomRequest request = BookRoomRequest.builder()
                .roomId("room-101-id")
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> bookingService.bookRoom(request, alice))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("You already hold a reservation for room(s)");
    }

    @Test
    @DisplayName("Should translate database DuplicateKeyException into 409 Conflict for race condition")
    void shouldHandleConcurrentRaceCondition() {
        duplicateOnSave.set(true);

        BookRoomRequest request = BookRoomRequest.builder()
                .roomId("room-101-id")
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> bookingService.bookRoom(request, alice))
                .isInstanceOf(BatchBookingConflictException.class)
                .hasMessageContaining("One or more selected rooms are unavailable");
    }
}
