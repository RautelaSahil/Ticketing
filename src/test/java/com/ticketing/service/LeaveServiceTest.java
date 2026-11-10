package com.ticketing.service;

import com.mongodb.client.result.UpdateResult;
import com.ticketing.dto.LeaveResponse;
import com.ticketing.model.ReservationStatus;
import com.ticketing.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.security.authentication.BadCredentialsException;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveServiceTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:02:30.000Z");

    private AtomicLong modifiedCount;
    private LeaveService leaveService;
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        modifiedCount = new AtomicLong(1L);

        MongoOperations mongoOperations = (MongoOperations) Proxy.newProxyInstance(
                MongoOperations.class.getClassLoader(),
                new Class<?>[]{MongoOperations.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateFirst")) {
                        long count = modifiedCount.get();
                        return UpdateResult.acknowledged(count, count, null);
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        leaveService = new LeaveService(fixedClock, mongoOperations);

        alice = UserPrincipal.create("user-alice", "alice", "ALICE", "hash", Set.of("ROLE_USER"));
    }

    @Test
    @DisplayName("Should successfully vacate an active room reservation and set status to CANCELLED")
    void shouldLeaveActiveRoomSuccessfully() {
        modifiedCount.set(1L);

        LeaveResponse response = leaveService.leaveRoom("res-123", alice);

        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isEqualTo("res-123");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.getMessage()).isEqualTo("Room vacated successfully");
    }

    @Test
    @DisplayName("Should throw 400 IllegalArgumentException when zero documents match the query")
    void shouldThrowWhenNoActiveReservationMatches() {
        // Simulates slot already elapsed, wrong user, or duplicate leave call
        modifiedCount.set(0L);

        assertThatThrownBy(() -> leaveService.leaveRoom("res-123", alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No active reservation found to release");
    }

    @Test
    @DisplayName("Should reject blank reservation ID")
    void shouldRejectBlankReservationId() {
        assertThatThrownBy(() -> leaveService.leaveRoom("   ", alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reservation ID cannot be empty");
    }

    @Test
    @DisplayName("Should reject unauthenticated call")
    void shouldRejectUnauthenticatedUser() {
        assertThatThrownBy(() -> leaveService.leaveRoom("res-123", null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Authentication is required to leave a room");
    }
}
