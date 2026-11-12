package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.FutureBookRequest;
import com.ticketing.dto.FutureBookingResponse;
import com.ticketing.exception.ResourceConflictException;
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
import org.springframework.dao.DuplicateKeyException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FutureBookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private BookingProperties bookingProperties;
    private Clock fixedClock;
    private FutureBookingService futureBookingService;

    // Fixed now: 12:02:00 UTC
    // T_next = 12:05:00 UTC
    private final Instant now = Instant.parse("2026-11-10T12:02:00Z");

    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setFutureWindowHours(24);
        bookingProperties.setCancellationCutoffMinutes(10);
        bookingProperties.setMaxFutureReservationsPerUser(12);

        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        futureBookingService = new FutureBookingService(
                roomRepository,
                reservationRepository,
                bookingProperties,
                fixedClock
        );

        alice = new UserPrincipal("u-alice", "alice", "Alice", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Rejects reservation with misaligned 5-minute boundary")
    void reserveFutureSlot_misalignedBoundary_rejected() {
        // 12:07:00 is not on 5-minute boundary
        Instant misaligned = Instant.parse("2026-11-10T12:07:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", misaligned);

        assertThatThrownBy(() -> futureBookingService.reserveFutureSlot(request, alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5-minute boundary");
    }

    @Test
    @DisplayName("Rejects reservation outside the 24-hour window")
    void reserveFutureSlot_outsideWindow_rejected() {
        // 25 hours ahead, on a valid 5-minute boundary (13:00)
        Instant tooFar = Instant.parse("2026-11-11T13:05:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", tooFar);

        assertThatThrownBy(() -> futureBookingService.reserveFutureSlot(request, alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within the booking window");
    }

    @Test
    @DisplayName("Rejects when user reaches cap of 12 live reservations")
    void reserveFutureSlot_userCapExceeded_rejected() {
        Instant validSlot = Instant.parse("2026-11-10T12:20:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", validSlot);

        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        // Already at limit 12
        when(reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                alice.getId(), now, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(12L);

        assertThatThrownBy(() -> futureBookingService.reserveFutureSlot(request, alice))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("reached your limit of 12");
    }

    @Test
    @DisplayName("Rejects when user already holds reservation for this room and slot")
    void reserveFutureSlot_duplicateBySameUser_rejected() {
        Instant validSlot = Instant.parse("2026-11-10T12:20:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", validSlot);

        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        when(reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                alice.getId(), now, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(2L);

        when(reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                alice.getId(), "room-1", validSlot, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(true);

        assertThatThrownBy(() -> futureBookingService.reserveFutureSlot(request, alice))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("already reserved this room for this interval");
    }

    @Test
    @DisplayName("Direct BOOKED insertion succeeds when slot is free")
    void reserveFutureSlot_freeSlot_directlyBooked() {
        Instant validSlot = Instant.parse("2026-11-10T12:20:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", validSlot);

        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        when(reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                alice.getId(), now, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(1L);

        when(reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                alice.getId(), "room-1", validSlot, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(false);

        Reservation savedReservation = Reservation.builder()
                .id("res-new-1")
                .roomId("room-1")
                .userId(alice.getId())
                .slotStart(validSlot)
                .slotEnd(validSlot.plus(Duration.ofMinutes(5)))
                .status(ReservationStatus.BOOKED)
                .build();

        when(reservationRepository.insert(any(Reservation.class))).thenReturn(savedReservation);

        FutureBookingResponse response = futureBookingService.reserveFutureSlot(request, alice);

        assertThat(response.getReservationId()).isEqualTo("res-new-1");
        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        assertThat(response.getMessage()).isEqualTo("Reservation confirmed");
    }

    @Test
    @DisplayName("Race on free slot: duplicate key on BOOKED places user into SCHEDULED queue if > 10m left")
    void reserveFutureSlot_raceOnFreeSlot_outside10m_queuesAsScheduled() {
        // 12:20 is 18 mins away (> 10m)
        Instant validSlot = Instant.parse("2026-11-10T12:20:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", validSlot);

        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        when(reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                alice.getId(), now, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(0L);

        when(reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                alice.getId(), "room-1", validSlot, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(false);

        // First insert (BOOKED) fails due to race condition with another user
        Reservation savedScheduled = Reservation.builder()
                .id("res-sched-1")
                .roomId("room-1")
                .userId(alice.getId())
                .slotStart(validSlot)
                .slotEnd(validSlot.plus(Duration.ofMinutes(5)))
                .status(ReservationStatus.SCHEDULED)
                .build();

        when(reservationRepository.insert(any(Reservation.class)))
                .thenThrow(new DuplicateKeyException("uniq_booked_room_slot duplicate"))
                .thenReturn(savedScheduled);

        FutureBookingResponse response = futureBookingService.reserveFutureSlot(request, alice);

        assertThat(response.getReservationId()).isEqualTo("res-sched-1");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.SCHEDULED);
        assertThat(response.getMessage()).contains("joined the waiting list");
    }

    @Test
    @DisplayName("Race on free slot: duplicate key on BOOKED rejects with 409 if <= 10m left")
    void reserveFutureSlot_raceOnFreeSlot_inside10m_rejected() {
        // 12:10 is 8 mins away (<= 10m cutoff)
        Instant validSlot = Instant.parse("2026-11-10T12:10:00Z");
        FutureBookRequest request = new FutureBookRequest("room-1", validSlot);

        Room room = Room.builder().id("room-1").roomNumber("101").active(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        when(reservationRepository.countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
                alice.getId(), now, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(0L);

        when(reservationRepository.existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
                alice.getId(), "room-1", validSlot, List.of(ReservationStatus.BOOKED, ReservationStatus.SCHEDULED)))
                .thenReturn(false);

        // First insert (BOOKED) fails due to race condition with another user
        when(reservationRepository.insert(any(Reservation.class)))
                .thenThrow(new DuplicateKeyException("uniq_booked_room_slot duplicate"));

        assertThatThrownBy(() -> futureBookingService.reserveFutureSlot(request, alice))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("cannot be queued within 10 minutes");
    }
}
