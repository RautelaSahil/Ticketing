package com.ticketing.scheduler;

import com.mongodb.client.result.UpdateResult;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Background scheduler that tidies elapsed reservations.
 * Transitions reservations with status BOOKED and slotEnd <= now to EXPIRED.
 * 
 * Note: Query correctness does NOT depend on this job running on time,
 * because queries compare slotStart and slotEnd temporally.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ReservationExpiryScheduler implements ApplicationRunner {

    private final Clock clock;
    private final MongoOperations mongoOperations;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running startup catch-up to expire any reservations elapsed during downtime...");
        long count = expireElapsedReservations();
        log.info("Startup expiration catch-up complete. Expired {} reservation(s).", count);
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public long expireElapsedReservations() {
        Instant now = clock.instant();

        Query query = new Query(
                Criteria.where("status").is(ReservationStatus.BOOKED)
                        .and("slotEnd").lte(now)
        );

        Update update = new Update()
                .set("status", ReservationStatus.EXPIRED)
                .set("updatedAt", now);

        UpdateResult result = mongoOperations.updateMulti(query, update, Reservation.class);
        long modifiedCount = result.getModifiedCount();

        if (modifiedCount > 0) {
            log.info("Expired {} elapsed BOOKED reservation(s) at {}", modifiedCount, now);
        } else {
            log.debug("No elapsed BOOKED reservations to expire at {}", now);
        }

        return modifiedCount;
    }
}
