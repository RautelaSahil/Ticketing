package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.SlotAction;
import com.ticketing.dto.SlotTimelineResponse;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotTimelineServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private BookingProperties bookingProperties;
    private Clock fixedClock;
    private SlotTimelineService timelineService;

    // Fixed point in time: 12:02:00 UTC
    // T_next = 12:05:00 UTC
    private final Instant now = Instant.parse("2026-11-10T12:02:00Z");

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setFutureWindowHours(24);
        bookingProperties.setCancellationCutoffMinutes(10);
        bookingProperties.setMaxFutureReservationsPerUser(12);

        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        timelineService = new SlotTimelineService(roomRepository, reservationRepository, bookingProperties, fixedClock);
    }

    private UserPrincipal createUser(String id, String username) {
        return new UserPrincipal(id, username, username, "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when room does not exist")
    void getTimeline_roomNotFound() {
        when(roomRepository.findById("room-999")).thenReturn(Optional.empty());

        UserPrincipal alice = createUser("u-1", "alice");
        assertThatThrownBy(() -> timelineService.getTimelineForRoom("room-999", alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when room is inactive")
    void getTimeline_roomInactive() {
        Room room = Room.builder().id("room-1").roomNumber("101").active(false).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        UserPrincipal alice = createUser("u-1", "alice");
        assertThatThrownBy(() -> timelineService.getTimelineForRoom("room-1", alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room is inactive");
    }

    @Test
    @DisplayName("Computes correct slot actions across window: BOOK, UNAVAILABLE, JOIN_QUEUE, YOURS_BOOKED, YOURS_SCHEDULED")
    void getTimeline_computesCorrectSlotActions() {
        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        // T_next = 12:05 (gap = 3 mins <= 10m cutoff)
        Instant slot1 = Instant.parse("2026-11-10T12:05:00Z");
        // slot2 = 12:10 (gap = 8 mins <= 10m cutoff)
        Instant slot2 = Instant.parse("2026-11-10T12:10:00Z");
        // slot3 = 12:15 (gap = 13 mins > 10m cutoff)
        Instant slot3 = Instant.parse("2026-11-10T12:15:00Z");
        // slot4 = 12:20 (gap = 18 mins > 10m cutoff) - booked by alice
        Instant slot4 = Instant.parse("2026-11-10T12:20:00Z");
        // slot5 = 12:25 (gap = 23 mins > 10m cutoff) - scheduled by alice
        Instant slot5 = Instant.parse("2026-11-10T12:25:00Z");

        UserPrincipal alice = createUser("user-alice", "alice");

        List<Reservation> mockReservations = List.of(
                // slot2 held by bob (inside 10m -> should be UNAVAILABLE for alice)
                Reservation.builder()
                        .id("r-bob-1")
                        .roomId("room-1")
                        .userId("user-bob")
                        .slotStart(slot2)
                        .slotEnd(slot2.plus(Duration.ofMinutes(5)))
                        .status(ReservationStatus.BOOKED)
                        .build(),
                // slot3 held by bob (outside 10m -> should be JOIN_QUEUE for alice)
                Reservation.builder()
                        .id("r-bob-2")
                        .roomId("room-1")
                        .userId("user-bob")
                        .slotStart(slot3)
                        .slotEnd(slot3.plus(Duration.ofMinutes(5)))
                        .status(ReservationStatus.BOOKED)
                        .build(),
                // slot4 booked by alice -> YOURS_BOOKED, cancellable=true
                Reservation.builder()
                        .id("r-alice-1")
                        .roomId("room-1")
                        .userId("user-alice")
                        .slotStart(slot4)
                        .slotEnd(slot4.plus(Duration.ofMinutes(5)))
                        .status(ReservationStatus.BOOKED)
                        .build(),
                // slot5 held by bob (BOOKED) and queued by alice (SCHEDULED) -> YOURS_SCHEDULED, cancellable=true
                Reservation.builder()
                        .id("r-bob-3")
                        .roomId("room-1")
                        .userId("user-bob")
                        .slotStart(slot5)
                        .slotEnd(slot5.plus(Duration.ofMinutes(5)))
                        .status(ReservationStatus.BOOKED)
                        .build(),
                Reservation.builder()
                        .id("r-alice-2")
                        .roomId("room-1")
                        .userId("user-alice")
                        .slotStart(slot5)
                        .slotEnd(slot5.plus(Duration.ofMinutes(5)))
                        .status(ReservationStatus.SCHEDULED)
                        .build()
        );

        when(reservationRepository.findByRoomIdAndSlotStartBetweenAndStatusIn(
                eq("room-1"), any(Instant.class), any(Instant.class), any()))
                .thenReturn(mockReservations);

        SlotTimelineResponse response = timelineService.getTimelineForRoom("room-1", alice);

        assertThat(response.getRoomId()).isEqualTo("room-1");
        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getServerTime()).isEqualTo(now);
        assertThat(response.getSlots()).hasSize(288); // 24 hours * 12 slots/hour

        // slot1 (12:05): empty -> BOOK
        var item1 = response.getSlots().get(0);
        assertThat(item1.getSlotStart()).isEqualTo(slot1);
        assertThat(item1.getAction()).isEqualTo(SlotAction.BOOK);
        assertThat(item1.getWaitingCount()).isEqualTo(0);
        assertThat(item1.isCancellable()).isFalse();

        // slot2 (12:10): booked by bob, gap 8m <= 10m -> UNAVAILABLE
        var item2 = response.getSlots().get(1);
        assertThat(item2.getSlotStart()).isEqualTo(slot2);
        assertThat(item2.getAction()).isEqualTo(SlotAction.UNAVAILABLE);
        assertThat(item2.isCancellable()).isFalse();

        // slot3 (12:15): booked by bob, gap 13m > 10m -> JOIN_QUEUE
        var item3 = response.getSlots().get(2);
        assertThat(item3.getSlotStart()).isEqualTo(slot3);
        assertThat(item3.getAction()).isEqualTo(SlotAction.JOIN_QUEUE);
        assertThat(item3.isCancellable()).isFalse();

        // slot4 (12:20): booked by alice -> YOURS_BOOKED, cancellable=true
        var item4 = response.getSlots().get(3);
        assertThat(item4.getSlotStart()).isEqualTo(slot4);
        assertThat(item4.getAction()).isEqualTo(SlotAction.YOURS_BOOKED);
        assertThat(item4.isCancellable()).isTrue();
        assertThat(item4.getCancellationDeadline()).isEqualTo(slot4.minus(Duration.ofMinutes(10)));

        // slot5 (12:25): queued by alice -> YOURS_SCHEDULED, cancellable=true, waitingCount=1
        var item5 = response.getSlots().get(4);
        assertThat(item5.getSlotStart()).isEqualTo(slot5);
        assertThat(item5.getAction()).isEqualTo(SlotAction.YOURS_SCHEDULED);
        assertThat(item5.isCancellable()).isTrue();
        assertThat(item5.getWaitingCount()).isEqualTo(1);
    }
}
