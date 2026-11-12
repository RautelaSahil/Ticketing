package com.ticketing.repository;

import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends MongoRepository<Reservation, String> {

    // Phase 2 grid: BOOKED rows for the next interval. Rooms in the result are red.
    List<Reservation> findBySlotStartAndStatus(Instant slotStart, ReservationStatus status);

    // Phase 2 "My Active Room": slotStart <= now < slotEnd. Returns a list because
    // a user can hold several rooms in the same interval (Phase 3).
    @Query("{ 'userId': ?0, 'status': 'BOOKED', 'slotStart': { '$lte': ?1 }, 'slotEnd': { '$gt': ?1 } }")
    List<Reservation> findActiveBookedByUser(String userId, Instant now);

    // Phase 3 collision check (status = BOOKED) and Phase 5 scheduled queue
    // (status = SCHEDULED, with List.of(roomId) for a single room)
    List<Reservation> findByRoomIdInAndSlotStartAndStatus(
            Collection<String> roomIds, Instant slotStart, ReservationStatus status);

    // Phase 4 duplicate check: user already BOOKED or SCHEDULED for this room and slot
    boolean existsByUserIdAndRoomIdAndSlotStartAndStatusIn(
            String userId, String roomId, Instant slotStart, Collection<ReservationStatus> statuses);

    // Phase 3 multi-room user duplicate check
    List<Reservation> findByUserIdAndRoomIdInAndSlotStartAndStatusIn(
            String userId, Collection<String> roomIds, Instant slotStart, Collection<ReservationStatus> statuses);

    // Phase 4 room timeline query: all reservations for a room across a time window with given statuses
    List<Reservation> findByRoomIdAndSlotStartBetweenAndStatusIn(
            String roomId, Instant start, Instant end, Collection<ReservationStatus> statuses);

    // Phase 4 user cap check: count upcoming live reservations (slotStart >= now)
    long countByUserIdAndSlotStartGreaterThanEqualAndStatusIn(
            String userId, Instant from, Collection<ReservationStatus> statuses);

    // Phase 4 user upcoming reservations: soonest first
    List<Reservation> findByUserIdAndSlotStartGreaterThanEqualAndStatusInOrderBySlotStartAsc(
            String userId, Instant from, Collection<ReservationStatus> statuses);

    // A user's own reservations, soonest first, so they can view and revoke future slots
    List<Reservation> findByUserIdAndStatusInOrderBySlotStartAsc(
            String userId, Collection<ReservationStatus> statuses);
}