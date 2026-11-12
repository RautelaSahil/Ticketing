package com.ticketing.service;

import com.mongodb.MongoException;
import com.ticketing.dto.BatchBookingResponse;
import com.ticketing.dto.BookBatchRequest;
import com.ticketing.dto.BookRoomRequest;
import com.ticketing.dto.ReservationResponse;
import com.ticketing.exception.BatchBookingConflictException;
import com.ticketing.exception.ResourceConflictException;
import com.ticketing.model.Reservation;
import com.ticketing.model.ReservationStatus;
import com.ticketing.model.Room;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.repository.RoomRepository;
import com.ticketing.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Public orchestrator service for Phase 3 batch bookings.
 * Sits outside the @Transactional method to handle retries for transient
 * write conflicts and translates race collisions into clean 409 responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchBookingService {

    private static final int MAX_TRANSIENT_RETRIES = 3;

    private final BatchBookingTransactionalService transactionalService;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    /**
     * Books a batch of up to 5 rooms for the next interval with all-or-nothing semantics.
     * Retries only transient write conflicts; duplicate key collisions immediately throw 409.
     */
    public BatchBookingResponse bookBatch(UserPrincipal currentUser, BookBatchRequest request) {
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                return transactionalService.executeBatchBooking(currentUser, request);
            } catch (DuplicateKeyException ex) {
                // Not retried: a concurrent transaction won the slot and committed to MongoDB
                log.warn("Batch booking race collision (DuplicateKey) on attempt {}: {}", attempt, ex.getMessage());
                throw createConflictFromCurrentState(request);
            } catch (Exception ex) {
                if (isTransientConflict(ex) && attempt < MAX_TRANSIENT_RETRIES) {
                    log.warn("Transient write conflict on attempt {}/{}. Retrying...", attempt, MAX_TRANSIENT_RETRIES);
                    backoff(attempt);
                    continue;
                }

                if (isTransientConflict(ex)) {
                    log.error("Exhausted all {} retries on transient transaction conflicts", MAX_TRANSIENT_RETRIES);
                    throw createConflictFromCurrentState(request);
                }

                // Non-transient exceptions (IllegalArgumentException, ResourceNotFoundException,
                // BatchBookingConflictException, etc.) propagate directly without retry.
                throw ex;
            }
        }
    }

    /**
     * Routes single-room booking through the identical batch pipeline with N=1.
     */
    public ReservationResponse bookSingleRoom(UserPrincipal currentUser, BookRoomRequest request) {
        BookBatchRequest batchRequest = BookBatchRequest.builder()
                .roomIds(List.of(request.getRoomId()))
                .slotStart(request.getSlotStart())
                .build();

        BatchBookingResponse batchResponse = bookBatch(currentUser, batchRequest);
        return batchResponse.getReservations().get(0);
    }

    /**
     * Determines which requested rooms are now occupied and produces a detailed 409 exception.
     */
    private BatchBookingConflictException createConflictFromCurrentState(BookBatchRequest request) {
        List<String> requestedIds = request.getRoomIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            return new BatchBookingConflictException(
                    "One or more selected rooms are unavailable. No reservations were made.",
                    List.of()
            );
        }

        List<Reservation> booked = reservationRepository.findByRoomIdInAndSlotStartAndStatus(
                requestedIds, request.getSlotStart(), ReservationStatus.BOOKED
        );

        List<String> unavailableRoomNumbers = booked.stream()
                .map(r -> {
                    Room room = roomRepository.findById(r.getRoomId()).orElse(null);
                    return room != null ? room.getRoomNumber() : r.getRoomId();
                })
                .distinct()
                .sorted()
                .toList();

        return new BatchBookingConflictException(
                "One or more selected rooms are unavailable. No reservations were made.",
                unavailableRoomNumbers
        );
    }

    private boolean isTransientConflict(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TransientDataAccessException) {
                return true;
            }
            if (current instanceof MongoException mongoEx) {
                if (mongoEx.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        || mongoEx.getCode() == 112) { // 112 = WriteConflict
                    return true;
                }
            }
            String msg = current.getMessage();
            if (msg != null && (msg.contains("WriteConflict") || msg.contains("TransientTransactionError"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void backoff(int attempt) {
        try {
            // Exponential backoff with basic jitter (30ms, 60ms, etc.)
            long sleepMs = (long) (Math.pow(2, attempt) * 15 + Math.random() * 15);
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
