package com.ticketing.service;

import com.ticketing.config.BookingProperties;
import com.ticketing.dto.CancelReservationResponse;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
class CancelServiceTest {

    @Mock
    private MongoOperations mongoOperations;

    @Mock
    private ReservationRepository reservationRepository;

    private BookingProperties bookingProperties;
    private Clock fixedClock;
    private CancelService cancelService;

    // Fixed now: 12:00:00 UTC
    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");

    private UserPrincipal alice;
    private UserPrincipal bob;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setFutureWindowHours(24);
        bookingProperties.setCancellationCutoffMinutes(10);
        bookingProperties.setMaxFutureReservationsPerUser(12);

        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        cancelService = new CancelService(
                mongoOperations,
                reservationRepository,
                bookingProperties,
                fixedClock
        );

        alice = new UserPrincipal("u-alice", "alice", "Alice", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        bob = new UserPrincipal("u-bob", "bob", "Bob", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Cancel succeeds when slot is > 10 minutes away (e.g. 10m01s)")
    void cancelReservation_outside10m_succeeds() {
        // slot at 12:10:01 (10m01s away)
        Instant slotStart = now.plus(Duration.ofMinutes(10)).plusSeconds(1);

        Reservation original = Reservation.builder()
                .id("res-1")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(slotStart)
                .status(ReservationStatus.BOOKED)
                .build();

        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(original);

        CancelReservationResponse response = cancelService.cancelReservation("res-1", alice);

        assertThat(response.getReservationId()).isEqualTo("res-1");
        assertThat(response.getPreviousStatus()).isEqualTo(ReservationStatus.BOOKED);
        assertThat(response.getCancelledAt()).isEqualTo(now);
        assertThat(response.getMessage()).isEqualTo("Reservation successfully cancelled");
    }

    @Test
    @DisplayName("Cancel succeeds for SCHEDULED status when > 10 minutes away")
    void cancelReservation_scheduledStatus_succeeds() {
        Instant slotStart = now.plus(Duration.ofMinutes(15));

        Reservation original = Reservation.builder()
                .id("res-sched")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(slotStart)
                .status(ReservationStatus.SCHEDULED)
                .build();

        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(original);

        CancelReservationResponse response = cancelService.cancelReservation("res-sched", alice);

        assertThat(response.getReservationId()).isEqualTo("res-sched");
        assertThat(response.getPreviousStatus()).isEqualTo(ReservationStatus.SCHEDULED);
    }

    @Test
    @DisplayName("Cancel fails when slot is exactly 10m00s away")
    void cancelReservation_exactly10m_fails() {
        Instant slotStart = now.plus(Duration.ofMinutes(10));

        // findAndModify matches nothing
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(null);

        Reservation existing = Reservation.builder()
                .id("res-boundary")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(slotStart)
                .status(ReservationStatus.BOOKED)
                .build();
        when(reservationRepository.findById("res-boundary")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cancelService.cancelReservation("res-boundary", alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel within 10 minutes");
    }

    @Test
    @DisplayName("Cancel fails when slot is < 10 minutes away (e.g. 9m59s)")
    void cancelReservation_inside10m_fails() {
        Instant slotStart = now.plus(Duration.ofMinutes(10)).minusSeconds(1);

        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(null);

        Reservation existing = Reservation.builder()
                .id("res-late")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(slotStart)
                .status(ReservationStatus.BOOKED)
                .build();
        when(reservationRepository.findById("res-late")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cancelService.cancelReservation("res-late", alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel within 10 minutes");
    }

    @Test
    @DisplayName("Cancel fails when reservation does not exist")
    void cancelReservation_notFound() {
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(null);
        when(reservationRepository.findById("res-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelService.cancelReservation("res-999", alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reservation not found");
    }

    @Test
    @DisplayName("Cancel fails when reservation belongs to another user")
    void cancelReservation_otherUser_rejected() {
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(null);

        Reservation bobRes = Reservation.builder()
                .id("res-bob")
                .userId(bob.getId())
                .roomId("room-1")
                .slotStart(now.plus(Duration.ofHours(2)))
                .status(ReservationStatus.BOOKED)
                .build();
        when(reservationRepository.findById("res-bob")).thenReturn(Optional.of(bobRes));

        // Alice tries to cancel Bob's reservation
        assertThatThrownBy(() -> cancelService.cancelReservation("res-bob", alice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reservation not found");
    }

    @Test
    @DisplayName("Cancel fails when reservation is already cancelled")
    void cancelReservation_alreadyCancelled() {
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reservation.class)))
                .thenReturn(null);

        Reservation cancelledRes = Reservation.builder()
                .id("res-cancelled")
                .userId(alice.getId())
                .roomId("room-1")
                .slotStart(now.plus(Duration.ofHours(2)))
                .status(ReservationStatus.CANCELLED)
                .build();
        when(reservationRepository.findById("res-cancelled")).thenReturn(Optional.of(cancelledRes));

        assertThatThrownBy(() -> cancelService.cancelReservation("res-cancelled", alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already cancelled");
    }
}
