package com.ticketing.service;

import com.ticketing.dto.BookRoomRequest;
import com.ticketing.dto.ReservationResponse;
import com.ticketing.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for single-room reservations.
 * In Phase 3, single-room reservations are routed through the unified
 * batch booking transactional pipeline with N=1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BatchBookingService batchBookingService;

    public ReservationResponse bookRoom(BookRoomRequest request, UserPrincipal currentUser) {
        if (request == null || request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be empty");
        }

        return batchBookingService.bookSingleRoom(currentUser, request);
    }
}
