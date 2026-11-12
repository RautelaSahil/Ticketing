package com.ticketing.service;

import com.mongodb.MongoException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchBookingServiceTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:01:30.000Z");
    private final Instant validNextStart = Instant.parse("2026-11-10T12:05:00.000Z");
    private final Instant expectedNextEnd = Instant.parse("2026-11-10T12:10:00.000Z");

    private Map<String, Room> mockRooms;
    private List<Reservation> existingBookings;
    private List<Reservation> savedReservations;
    private AtomicBoolean duplicateOnSave;
    private AtomicInteger transientFailuresRemaining;
    private AtomicBoolean userAlreadyHoldsRoom;

    private BatchBookingService batchBookingService;
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        mockRooms = new HashMap<>();
        existingBookings = new ArrayList<>();
        savedReservations = new ArrayList<>();
        duplicateOnSave = new AtomicBoolean(false);
        transientFailuresRemaining = new AtomicInteger(0);
        userAlreadyHoldsRoom = new AtomicBoolean(false);

        // Seed 6 test rooms
        mockRooms.put("room-101-id", Room.builder().id("room-101-id").roomNumber("101").capacity(4).active(true).build());
        mockRooms.put("room-102-id", Room.builder().id("room-102-id").roomNumber("102").capacity(6).active(true).build());
        mockRooms.put("room-103-id", Room.builder().id("room-103-id").roomNumber("103").capacity(2).active(true).build());
        mockRooms.put("room-201-id", Room.builder().id("room-201-id").roomNumber("201").capacity(8).active(true).build());
        mockRooms.put("room-202-id", Room.builder().id("room-202-id").roomNumber("202").capacity(4).active(true).build());
        mockRooms.put("room-203-id", Room.builder().id("room-203-id").roomNumber("203").capacity(10).active(true).build());

        RoomRepository roomRepository = (RoomRepository) Proxy.newProxyInstance(
                RoomRepository.class.getClassLoader(),
                new Class<?>[]{RoomRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAllById")) {
                        @SuppressWarnings("unchecked")
                        Iterable<String> ids = (Iterable<String>) args[0];
                        List<Room> result = new ArrayList<>();
                        for (String id : ids) {
                            Room r = mockRooms.get(id);
                            if (r != null) result.add(r);
                        }
                        return result;
                    }
                    if (method.getName().equals("findById")) {
                        String id = (String) args[0];
                        return java.util.Optional.ofNullable(mockRooms.get(id));
                    }
                    return null;
                }
        );

        ReservationRepository reservationRepository = (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class<?>[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUserIdAndRoomIdInAndSlotStartAndStatusIn")) {
                        if (userAlreadyHoldsRoom.get()) {
                            return List.of(Reservation.builder()
                                    .id("user-res-1")
                                    .roomId("room-101-id")
                                    .userId("user-alice")
                                    .slotStart(validNextStart)
                                    .status(ReservationStatus.BOOKED)
                                    .build());
                        }
                        return List.of();
                    }
                    if (method.getName().equals("findByRoomIdInAndSlotStartAndStatus")) {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<String> requestedIds = (java.util.Collection<String>) args[0];
                        List<Reservation> conflicts = new ArrayList<>();
                        for (Reservation r : existingBookings) {
                            if (requestedIds.contains(r.getRoomId()) && r.getSlotStart().equals(args[1])) {
                                conflicts.add(r);
                            }
                        }
                        return conflicts;
                    }
                    if (method.getName().equals("saveAll")) {
                        if (transientFailuresRemaining.getAndDecrement() > 0) {
                            MongoException transientEx = new MongoException(112, "WriteConflict");
                            transientEx.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                            throw transientEx;
                        }
                        if (duplicateOnSave.get()) {
                            // Simulate concurrent commit winning room 101
                            existingBookings.add(Reservation.builder()
                                    .id("bob-winner")
                                    .roomId("room-101-id")
                                    .userId("user-bob")
                                    .slotStart(validNextStart)
                                    .status(ReservationStatus.BOOKED)
                                    .build());
                            throw new DuplicateKeyException("E11000 duplicate key error on index uniq_booked_room_slot");
                        }
                        @SuppressWarnings("unchecked")
                        Iterable<Reservation> list = (Iterable<Reservation>) args[0];
                        List<Reservation> saved = new ArrayList<>();
                        int counter = 1;
                        for (Reservation r : list) {
                            r.setId("res-assigned-" + counter++);
                            saved.add(r);
                            savedReservations.add(r);
                        }
                        return saved;
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        BookingProperties properties = new BookingProperties();
        properties.setMaxRoomsPerRequest(5);

        BatchBookingTransactionalService txService = new BatchBookingTransactionalService(
                fixedClock, roomRepository, reservationRepository, properties
        );

        batchBookingService = new BatchBookingService(
                txService, reservationRepository, roomRepository
        );

        alice = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));
    }

    @Test
    @DisplayName("Should successfully book a batch of 3 rooms when all are free")
    void shouldBookBatchSuccessfully() {
        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id", "room-103-id"))
                .slotStart(validNextStart)
                .build();

        BatchBookingResponse response = batchBookingService.bookBatch(alice, request);

        assertThat(response).isNotNull();
        assertThat(response.getSlotStart()).isEqualTo(validNextStart);
        assertThat(response.getSlotEnd()).isEqualTo(expectedNextEnd);
        assertThat(response.getReservations()).hasSize(3);

        List<String> bookedNumbers = new ArrayList<>();
        for (ReservationResponse res : response.getReservations()) {
            bookedNumbers.add(res.getRoomNumber());
            assertThat(res.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        }
        assertThat(bookedNumbers).containsExactlyInAnyOrder("101", "102", "103");
        assertThat(savedReservations).hasSize(3);
    }

    @Test
    @DisplayName("Should rollback and throw 409 Conflict when 1 of 3 requested rooms is already occupied")
    void shouldRollbackWhenOneRoomIsOccupied() {
        // Room 102 already booked by Bob
        existingBookings.add(Reservation.builder()
                .id("bob-res")
                .roomId("room-102-id")
                .userId("user-bob")
                .slotStart(validNextStart)
                .status(ReservationStatus.BOOKED)
                .build());

        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id", "room-103-id"))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, request))
                .isInstanceOf(BatchBookingConflictException.class)
                .satisfies(ex -> {
                    BatchBookingConflictException conflict = (BatchBookingConflictException) ex;
                    assertThat(conflict.getUnavailableRooms()).containsExactly("102");
                });

        // Atomic all-or-nothing rollback check: 0 reservations saved
        assertThat(savedReservations).isEmpty();
    }

    @Test
    @DisplayName("Should enforce cap: reject request with 6 unique rooms with 400 Bad Request")
    void shouldRejectWhenExceedingCap() {
        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id", "room-103-id", "room-201-id", "room-202-id", "room-203-id"))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot book more than 5 rooms per request");

        assertThat(savedReservations).isEmpty();
    }

    @Test
    @DisplayName("Should deduplicate room IDs before enforcing cap: [101, 101, 102] counts as 2 rooms")
    void shouldDeduplicateRoomIds() {
        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-101-id", "room-102-id"))
                .slotStart(validNextStart)
                .build();

        BatchBookingResponse response = batchBookingService.bookBatch(alice, request);

        assertThat(response).isNotNull();
        assertThat(response.getReservations()).hasSize(2);
        assertThat(savedReservations).hasSize(2);
    }

    @Test
    @DisplayName("Should reject empty or whitespace room list with 400 Bad Request")
    void shouldRejectEmptyRoomList() {
        BookBatchRequest emptyRequest = BookBatchRequest.builder()
                .roomIds(List.of("   ", ""))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, emptyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room selection cannot be empty");
    }

    @Test
    @DisplayName("Should reject nonexistent or inactive room with 404 Not Found")
    void shouldRejectMissingRoom() {
        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-999-missing"))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more rooms were not found or are inactive");

        assertThat(savedReservations).isEmpty();
    }

    @Test
    @DisplayName("Should reject request when user already holds one of the requested rooms with 409 Conflict")
    void shouldRejectWhenUserAlreadyHoldsRoom() {
        userAlreadyHoldsRoom.set(true);

        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id"))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("You already hold a reservation for room(s)");

        assertThat(savedReservations).isEmpty();
    }

    @Test
    @DisplayName("Should retry on transient write conflict and succeed on next attempt")
    void shouldRetryOnTransientWriteConflict() {
        // First attempt will fail with WriteConflict, second will succeed
        transientFailuresRemaining.set(1);

        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id"))
                .slotStart(validNextStart)
                .build();

        BatchBookingResponse response = batchBookingService.bookBatch(alice, request);

        assertThat(response).isNotNull();
        assertThat(response.getReservations()).hasSize(2);
        assertThat(savedReservations).hasSize(2);
    }

    @Test
    @DisplayName("Should translate database DuplicateKeyException into 409 Conflict naming the colliding room")
    void shouldHandleConcurrentRaceCollision() {
        duplicateOnSave.set(true);

        BookBatchRequest request = BookBatchRequest.builder()
                .roomIds(List.of("room-101-id", "room-102-id"))
                .slotStart(validNextStart)
                .build();

        assertThatThrownBy(() -> batchBookingService.bookBatch(alice, request))
                .isInstanceOf(BatchBookingConflictException.class)
                .satisfies(ex -> {
                    BatchBookingConflictException conflict = (BatchBookingConflictException) ex;
                    assertThat(conflict.getUnavailableRooms()).containsExactly("101");
                });

        assertThat(savedReservations).isEmpty();
    }
}
