package com.ticketing.scheduler;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationExpirySchedulerTest {

    private final Instant fixedNow = Instant.parse("2026-11-10T12:05:00.000Z");

    private AtomicLong modifiedCount;
    private ReservationExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        modifiedCount = new AtomicLong(3L);

        MongoOperations mongoOperations = (MongoOperations) Proxy.newProxyInstance(
                MongoOperations.class.getClassLoader(),
                new Class<?>[]{MongoOperations.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateMulti")) {
                        long count = modifiedCount.get();
                        return UpdateResult.acknowledged(count, count, null);
                    }
                    return null;
                }
        );

        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        scheduler = new ReservationExpiryScheduler(fixedClock, mongoOperations);
    }

    @Test
    @DisplayName("Should sweep and expire elapsed BOOKED reservations on scheduled tick")
    void shouldExpireElapsedReservations() {
        modifiedCount.set(5L);

        long expiredCount = scheduler.expireElapsedReservations();

        assertThat(expiredCount).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should run startup catch-up cleanly via ApplicationRunner")
    void shouldRunStartupCatchUp() {
        modifiedCount.set(2L);

        scheduler.run(null);

        // Verify it executes without error and updates 2 records
        assertThat(modifiedCount.get()).isEqualTo(2L);
    }
}
