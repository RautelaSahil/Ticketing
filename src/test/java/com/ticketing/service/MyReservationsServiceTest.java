package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.MyFutureReservationsResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyReservationsServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    private BookingProperties bookingProperties;
    private Clock fixedClock;
    private MyReservationsService myReservationsService;

    // Fixed now: 12:00:00 UTC
    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setFutureWindowHours(24);
        bookingProperties.setCancellationCutoffMinutes(10);
        bookingProperties.setMaxFutureReservationsPerUser(12);

        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        myReservationsService = new MyReservationsService(
                reservationRepository,
                roomRepository,
                bookingProperties,
                fixedClock
        );

        alice = new UserPrincipal("u-alice", "alice", "Alice", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Returns empty list when user has no upcoming reservations")
    void getMyFutureReservations_empty() {
        when(reservationRepository.findByUserIdAndSlotStartGreaterThanEqualAndStatusInOrderBySlotStartAsc(
                eq(alice.getId()), eq(now), any()))
                .thenReturn(List.of());

        MyFutureReservationsResponse response = myReservationsService.getMyFutureReservations(alice);

        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getReservations()).isEmpty();
        assertThat(response.getServerTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("Returns upcoming reservations with correct cancellable flags and deadlines")
    void getMyFutureReservations_withItems() {
        // slot1 is at 12:05 (gap = 5m <= 10m -> cancellable = false)
        Instant slot1 = now.plus(Duration.ofMinutes(5));
        // slot2 is at 12:30 (gap = 30m > 10m -> cancellable = true)
        Instant slot2 = now.plus(Duration.ofMinutes(30));

        Reservation res1 = Reservation.builder()
                .id("res-1")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(slot1)
                .slotEnd(slot1.plus(Duration.ofMinutes(5)))
                .status(ReservationStatus.BOOKED)
                .createdAt(now.minusSeconds(60))
                .build();

        Reservation res2 = Reservation.builder()
                .id("res-2")
                .userId(alice.getId())
                .roomId("room-2")
                .slotStart(slot2)
                .slotEnd(slot2.plus(Duration.ofMinutes(5)))
                .status(ReservationStatus.SCHEDULED)
                .createdAt(now.minusSeconds(30))
                .build();

        when(reservationRepository.findByUserIdAndSlotStartGreaterThanEqualAndStatusInOrderBySlotStartAsc(
                eq(alice.getId()), eq(now), any()))
                .thenReturn(List.of(res1, res2));

        Room r1 = Room.builder().id("room-1").roomNumber("101").build();
        Room r2 = Room.builder().id("room-2").roomNumber("102").build();
        when(roomRepository.findAllById(any())).thenReturn(List.of(r1, r2));

        MyFutureReservationsResponse response = myReservationsService.getMyFutureReservations(alice);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getReservations()).hasSize(2);

        var item1 = response.getReservations().get(0);
        assertThat(item1.getReservationId()).isEqualTo("res-1");
        assertThat(item1.getRoomNumber()).isEqualTo("101");
        assertThat(item1.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        assertThat(item1.isCancellable()).isFalse();
        assertThat(item1.getCancellationDeadline()).isEqualTo(slot1.minus(Duration.ofMinutes(10)));

        var item2 = response.getReservations().get(1);
        assertThat(item2.getReservationId()).isEqualTo("res-2");
        assertThat(item2.getRoomNumber()).isEqualTo("102");
        assertThat(item2.getStatus()).isEqualTo(ReservationStatus.SCHEDULED);
        assertThat(item2.isCancellable()).isTrue();
        assertThat(item2.getCancellationDeadline()).isEqualTo(slot2.minus(Duration.ofMinutes(10)));
    }
}
